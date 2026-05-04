package com.visionsoldier.app.commands

enum class CommandType {
    OPEN_APP,
    OPEN_WHATSAPP,
    SEND_SMS,
    MAKE_CALL,
    WEB_SEARCH,
    SET_ALARM,
    PLAY_MUSIC,
    OPEN_URL,
    AI_CHAT,
    // ── Nuevos: AccessibilityService ──
    A11Y_WRITE_WHATSAPP,   // Escribir y enviar mensaje real en WhatsApp
    A11Y_WRITE_INSTAGRAM,  // Escribir en Instagram
    A11Y_DELETE_CHAT,      // Borrar un chat en WhatsApp
    A11Y_CLEAN_CHATS,      // Borrar múltiples chats (TikTok/IG)
    A11Y_NAVIGATE,         // Ir atrás, home, recientes
    // ── Nuevos: Diario y Tareas ──
    ADD_DIARY,             // Guardar entrada en el diario
    ADD_TASK,              // Agregar tarea
    LIST_TASKS,            // Listar tareas activas
}

data class Command(
    val type: CommandType,
    val params: Map<String, String> = emptyMap(),
    val rawText: String = ""
)

class CommandRouter {

    fun route(input: String): Command {
        val lower = input.lowercase().trim()

        return when {

            // ── Diario: guardar entrada ──
            hasAny(lower, "guarda esto", "guarda en el diario", "anota esto", "apunta esto", "diario:") -> {
                val content = extractAfter(lower, "guarda esto:", "guarda en el diario:", "anota esto:", "apunta esto:", "diario:", "guarda esto", "guarda en el diario", "anota esto", "apunta esto")
                Command(CommandType.ADD_DIARY, mapOf("content" to content), input)
            }

            // ── Tareas: agregar ──
            hasAny(lower, "nueva tarea", "agrega tarea", "agregar tarea", "tarea:") -> {
                val task = extractAfter(lower, "nueva tarea:", "agrega tarea:", "agregar tarea:", "tarea:", "nueva tarea", "agrega tarea", "agregar tarea")
                Command(CommandType.ADD_TASK, mapOf("text" to task), input)
            }

            // ── Tareas: listar ──
            hasAny(lower, "mis tareas", "qué tareas tengo", "lista de tareas", "tareas pendientes") -> {
                Command(CommandType.LIST_TASKS, emptyMap(), input)
            }

            // ── AccessibilityService: escribir en WhatsApp y enviar ──
            hasAny(lower, "escribe en whatsapp", "envía por whatsapp", "manda por whatsapp") &&
            !hasAny(lower, "abre whatsapp") -> {
                val info = extractContactAndMessage(lower)
                Command(CommandType.A11Y_WRITE_WHATSAPP, info, input)
            }

            // ── AccessibilityService: borrar múltiples chats ──
            hasAny(lower, "borra ", "elimina ", "limpia ") && hasAny(lower, " chats de", " mensajes de", " conversaciones de") && Regex("\\d+").containsMatchIn(lower) -> {
                val count = Regex("\\d+").find(lower)?.value ?: "1"
                val app = extractAfter(lower, "chats de ", "mensajes de ", "conversaciones de ").substringBefore(" ")
                Command(CommandType.A11Y_CLEAN_CHATS, mapOf("count" to count, "app" to app), input)
            }

            // ── AccessibilityService: borrar chat específico ──
            hasAny(lower, "borra el chat", "elimina el chat", "borra la conversación", "elimina la conversación") -> {
                val contact = extractAfter(lower, "borra el chat con", "elimina el chat con", "borra la conversación con", "elimina la conversación con", "borra el chat de", "elimina el chat de")
                Command(CommandType.A11Y_DELETE_CHAT, mapOf("contact" to contact), input)
            }

            // ── AccessibilityService: escribir en Instagram ──
            hasAny(lower, "escribe en instagram", "publica en instagram", "postea en instagram") -> {
                val text = extractAfter(lower, "escribe en instagram:", "publica en instagram:", "postea en instagram:", "escribe en instagram", "publica en instagram", "postea en instagram")
                Command(CommandType.A11Y_WRITE_INSTAGRAM, mapOf("text" to text), input)
            }

            // ── AccessibilityService: navegación global ──
            hasAny(lower, "ve atrás", "volver atrás", "regresa", "ir al inicio", "ir al home") && lower.length < 30 -> {
                val action = when {
                    hasAny(lower, "inicio", "home") -> "home"
                    else -> "back"
                }
                Command(CommandType.A11Y_NAVIGATE, mapOf("action" to action), input)
            }

            // ── Abrir app ──
            hasAny(lower, "abre", "abrir", "lanza", "abre la") -> {
                val app = extractAfter(lower, "abre la", "abre", "abrir", "lanza")
                Command(CommandType.OPEN_APP, mapOf("app" to app), input)
            }

            // ── WhatsApp / mensaje (modo Intent, no AccessibilityService) ──
            hasAny(lower, "whatsapp", "manda un mensaje", "escríbele", "escribe a") ||
            (hasAny(lower, "manda", "envía", "mensaje") && hasAny(lower, "a ")) -> {
                val info = extractContactAndMessage(lower)
                Command(CommandType.OPEN_WHATSAPP, info, input)
            }

            // ── SMS ──
            hasAny(lower, "sms", "mensaje de texto", "texto a") -> {
                val info = extractContactAndMessage(lower)
                Command(CommandType.SEND_SMS, info, input)
            }

            // ── Llamar ──
            hasAny(lower, "llama a", "llama al", "llamar a", "llama") && lower.contains("llama") -> {
                val contact = extractAfter(lower, "llama a", "llama al", "llamar a", "llama")
                Command(CommandType.MAKE_CALL, mapOf("contact" to contact), input)
            }

            // ── Buscar ──
            hasAny(lower, "busca", "buscar", "googlea", "busca en google", "búsqueda de") -> {
                val query = extractAfter(lower, "busca en google", "búsqueda de", "busca información sobre", "busca sobre", "busca", "buscar", "googlea")
                Command(CommandType.WEB_SEARCH, mapOf("query" to query), input)
            }

            // ── Alarma ──
            hasAny(lower, "alarma", "despiértame", "pon una alarma") -> {
                val time = extractAfter(lower, "pon una alarma a las", "alarma a las", "despiértame a las", "despiértame a", "alarma para", "alarma")
                Command(CommandType.SET_ALARM, mapOf("time" to time), input)
            }

            // ── Música ──
            hasAny(lower, "pon música", "reproduce", "música de", "canción") && hasAny(lower, "música", "reproduce", "canción") -> {
                val query = extractAfter(lower, "reproduce la canción", "pon la canción de", "pon música de", "música de", "reproduce", "pon")
                Command(CommandType.PLAY_MUSIC, mapOf("query" to query), input)
            }

            // ── URL directa ──
            lower.contains("http://") || lower.contains("https://") || lower.contains(".com") || lower.contains(".mx") -> {
                Command(CommandType.OPEN_URL, mapOf("url" to input.trim()), input)
            }

            // ── Conversación con IA ──
            else -> Command(CommandType.AI_CHAT, emptyMap(), input)
        }
    }

    private fun hasAny(text: String, vararg keywords: String) =
        keywords.any { text.contains(it) }

    private fun extractAfter(text: String, vararg prefixes: String): String {
        // Sort longest first to avoid partial matches
        for (prefix in prefixes.sortedByDescending { it.length }) {
            val idx = text.indexOf(prefix)
            if (idx != -1) {
                return text.substring(idx + prefix.length)
                    .replace(Regex("^[,.:!?;\\s]+"), "")
                    .trim()
            }
        }
        return text.trim()
    }

    private fun extractContactAndMessage(text: String): Map<String, String> {
        // Pattern: "a [contact]: [message]" or "a [contact] que [message]"
        val colonPattern = Regex("""(?:a|al|para)\s+([\w\s]+?)\s*[:\-]\s*(.+)""")
        val quePattern   = Regex("""(?:a|al|para)\s+([\w\s]+?)\s+(?:que|dile que|diciéndole)\s*:?\s*(.+)""")

        colonPattern.find(text)?.let {
            return mapOf("contact" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim())
        }
        quePattern.find(text)?.let {
            return mapOf("contact" to it.groupValues[1].trim(), "message" to it.groupValues[2].trim())
        }

        // Fallback: extract contact name only
        val contact = extractAfter(text, "mensaje a", "mensaje para", "whatsapp a", "escríbele a", "escribe a", "a")
        return mapOf("contact" to contact.substringBefore(" ").trim(), "message" to "")
    }
}
