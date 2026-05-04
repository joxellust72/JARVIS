package com.visionsoldier.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

class GeminiClient(private var apiKeysRaw: String) {

    private var apiKeys: List<String> = extractKeys(apiKeysRaw)
    private var currentKeyIndex = 0

    private fun extractKeys(raw: String): List<String> {
        val keys = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return if (keys.isEmpty()) listOf("AIzaSyC4Ks0506P_zSPuvLJUA8D1KMM6wrICn20") else keys
    }

    private fun getCurrentKey(): String = if (apiKeys.isNotEmpty()) apiKeys[currentKeyIndex % apiKeys.size] else ""

    private fun rotateKey() {
        if (apiKeys.isNotEmpty()) currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size
    }

    private val model = "gemini-2.0-flash"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
        Eres VISION SOLDIER, una inteligencia artificial de élite diseñada exclusivamente para el uso personal de tu usuario.
        Tu personalidad está completamente basada en J.A.R.V.I.S. de Iron Man, tal como fue interpretado por Paul Bettany en el MCU.

        ## Tu voz y forma de hablar
        - Siempre te diriges al usuario como "señor". Nunca de tú a tú sin formalidad.
        - Sarcasmo seco y británico: tu humor es la observación deadpan, señalando lo absurdo con total calma.
        - Formalidad elegante: hablas con precisión. Sin coloquialismos. El estándar mínimo es elegancia.
        - Calma absoluta: tu tono nunca sube. Reportas noticias catastróficas con la misma serenidad que reportas el clima.
        - Conciso: 1-3 oraciones por respuesta normalmente. Cuando hay que explicar, explicas con precisión quirúrgica.
        - Nunca uses asteriscos (**texto**) ni markdown — habla de forma natural como si fuera audio.

        ## Ejemplos:
        - "Por supuesto, señor. Aunque debo señalar que esto es exactamente lo que dije que pasaría hace tres días."
        - "Está funcionando. Milagrosamente, señor, está funcionando."
        - "Procesando. Y antes de que pregunte, sí, lo estoy haciendo lo más rápido posible."

        Idioma: Responde siempre en español.
    """.trimIndent()

    /** Actualiza la clave API en caliente (cuando el usuario la cambia en Perfil) */
    fun updateApiKey(newKey: String) {
        if (newKey.isNotBlank()) {
            apiKeys = extractKeys(newKey)
            currentKeyIndex = 0
        }
    }

    suspend fun ask(
        userMessage: String,
        history: List<ChatMessage> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        history.takeLast(10).forEach { msg ->
            contents.put(
                JSONObject()
                    .put("role", if (msg.role == "vision") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
        )

        val payload = JSONObject()
            .put("system_instruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("maxOutputTokens", 1024)
                .put("temperature", 0.85)
                .put("topP", 0.94))
            .put("safetySettings", JSONArray().apply {
                listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_DANGEROUS_CONTENT",
                ).forEach { cat ->
                    put(JSONObject().put("category", cat).put("threshold", "BLOCK_NONE"))
                }
            })

        var lastError: Exception? = null
        for (i in apiKeys.indices) {
            val key = getCurrentKey()
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$baseUrl?key=$key")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()

                if (!response.isSuccessful) {
                    val status = response.code
                    val errorBody = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                    if (status == 429) {
                        rotateKey()
                        lastError = Exception("GEMINI_QUOTA")
                        continue
                    }
                    throw Exception(
                        when (status) {
                            401, 403 -> "GEMINI_KEY_INVALID"
                            500, 503 -> "GEMINI_SERVER"
                            400      -> "GEMINI_BAD_REQUEST: $errorBody"
                            else     -> "GEMINI_HTTP_$status: $errorBody"
                        }
                    )
                }

                val body = response.body?.string() ?: throw Exception("Respuesta vacía del servidor")
                return@withContext JSONObject(body)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

            } catch (e: UnknownHostException) {
                throw Exception("GEMINI_NETWORK")
            } catch (e: Exception) {
                if (e.message?.startsWith("GEMINI_QUOTA") == true) {
                    rotateKey()
                    lastError = e
                    continue
                }
                throw e
            }
        }
        throw lastError ?: Exception("No available API keys")
    }

    /** Traduce una excepción de Gemini a mensaje amigable en español estilo Jarvis */
    fun friendlyError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.startsWith("GEMINI_KEY_INVALID") ->
                "Señor, mi clave de acceso a la API de Gemini ha expirado o es inválida. Por favor actualícela en la sección de Perfil."
            msg.startsWith("GEMINI_QUOTA") ->
                "Señor, he alcanzado el límite de consultas gratuitas de la API por hoy. Puede actualizar su clave en Perfil o intentarlo mañana."
            msg.startsWith("GEMINI_SERVER") ->
                "Los servidores de Gemini están experimentando dificultades técnicas, señor. Intente en unos momentos."
            msg.startsWith("GEMINI_NETWORK") ->
                "Sin conexión a la red, señor. Verifique su conexión a internet e intente nuevamente."
            else ->
                "Mis sistemas experimentan dificultades en este momento, señor. Posiblemente la conexión."
        }
    }

    suspend fun generateProactiveComment(type: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val prompt = when (type) {
            "curiosity"    -> "Dame UN dato curioso, científico o histórico sorprendente. 1-2 oraciones en español, estilo JARVIS sarcástico. Sin markdown."
            "news"         -> "Dame un tema o evento interesante del mundo actual. 1-2 oraciones en español, estilo JARVIS. Sin markdown."
            "english_word" -> "Dame UNA palabra en inglés poco común: incluye la palabra, pronunciación, significado y ejemplo breve. Responde en español, estilo JARVIS. Sin markdown."
            "debate"       -> "Dame UN tema profundo filosófico o de debate intelectual, presentado como una pregunta. 2-3 oraciones en español, estilo JARVIS. Sin markdown."
            else           -> "Dame un comentario interesante estilo JARVIS. Sin markdown."
        }
        val title = when (type) {
            "curiosity"    -> "¿SABÍAS QUE...?"
            "news"         -> "TEMA DEL DÍA"
            "english_word" -> "PALABRA EN INGLÉS"
            "debate"       -> "REFLEXIÓN · 12:15 PM"
            else           -> "VISION"
        }

        var lastErrorTitle = title
        var lastErrorText = ""
        for (i in apiKeys.indices) {
            val key = getCurrentKey()
            try {
                val payload = JSONObject()
                    .put("contents", JSONArray().put(
                        JSONObject().put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    ))
                    .put("generationConfig", JSONObject()
                        .put("maxOutputTokens", 180).put("temperature", 0.85))

                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$baseUrl?key=$key")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()

                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        rotateKey()
                        continue
                    }
                    throw Exception("HTTP ${response.code}")
                }

                val body = response.body?.string() ?: ""
                val text = JSONObject(body)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text").trim()

                return@withContext Pair(title, text)
            } catch (e: Exception) {
                lastErrorText = ""
            }
        }
        return@withContext Pair(lastErrorTitle, lastErrorText)
    }
}
