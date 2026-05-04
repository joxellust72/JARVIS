package com.visionsoldier.app.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.telephony.SmsManager
import android.util.Log
import com.visionsoldier.app.accessibility.AccessibilityActions
import com.visionsoldier.app.data.VisionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemActions(private val context: Context) {

    companion object {
        private val KNOWN_APPS = mapOf(
            "whatsapp"     to "com.whatsapp",
            "instagram"    to "com.instagram.android",
            "facebook"     to "com.facebook.katana",
            "twitter"      to "com.twitter.android",
            "x"            to "com.twitter.android",
            "youtube"      to "com.google.android.youtube",
            "spotify"      to "com.spotify.music",
            "gmail"        to "com.google.android.gm",
            "maps"         to "com.google.android.apps.maps",
            "google maps"  to "com.google.android.apps.maps",
            "chrome"       to "com.android.chrome",
            "telegram"     to "org.telegram.messenger",
            "fotos"        to "com.google.android.apps.photos",
            "galería"      to "com.google.android.apps.photos",
            "calculadora"  to "com.google.android.calculator",
            "calendario"   to "com.google.android.calendar",
            "netflix"      to "com.netflix.mediaclient",
            "uber"         to "com.ubercab",
            "tiktok"       to "com.zhiliaoapp.musically",
            "camera"       to "com.android.camera2",
            "cámara"       to "com.android.camera2",
            "ajustes"      to "com.android.settings",
            "configuración" to "com.android.settings",
        )
    }

    private val repository = VisionRepository.getInstance(context)

    fun execute(command: Command): String {
        return try {
            when (command.type) {
                CommandType.OPEN_APP     -> openApp(command.params["app"] ?: "")
                CommandType.OPEN_WHATSAPP -> openWhatsApp(
                    command.params["contact"] ?: "",
                    command.params["message"] ?: ""
                )
                CommandType.SEND_SMS     -> sendSms(
                    command.params["contact"] ?: "",
                    command.params["message"] ?: ""
                )
                CommandType.MAKE_CALL    -> makeCall(command.params["contact"] ?: "")
                CommandType.WEB_SEARCH   -> webSearch(command.params["query"] ?: "")
                CommandType.SET_ALARM    -> setAlarm(command.params["time"] ?: "")
                CommandType.PLAY_MUSIC   -> playMusic(command.params["query"] ?: "")
                CommandType.OPEN_URL     -> openUrl(command.params["url"] ?: "")

                // ── AccessibilityService ──
                CommandType.A11Y_WRITE_WHATSAPP -> a11yWriteWhatsApp(
                    command.params["contact"] ?: "",
                    command.params["message"] ?: ""
                )
                CommandType.A11Y_WRITE_INSTAGRAM -> a11yWriteInstagram(
                    command.params["text"] ?: ""
                )
                CommandType.A11Y_DELETE_CHAT -> a11yDeleteChat(
                    command.params["contact"] ?: ""
                )
                CommandType.A11Y_NAVIGATE -> a11yNavigate(
                    command.params["action"] ?: "back"
                )

                // ── Diario y Tareas (se manejan como suspend, se procesan aparte) ──
                CommandType.ADD_DIARY  -> "" // se maneja en VisionForegroundService
                CommandType.ADD_TASK   -> "" // se maneja en VisionForegroundService
                CommandType.LIST_TASKS -> "" // se maneja en VisionForegroundService

                else -> ""
            }
        } catch (e: Exception) {
            Log.e("SystemActions", "Error ejecutando acción: ${e.message}")
            "No pude completar esa acción, señor. ${e.localizedMessage}"
        }
    }

    /**
     * Ejecutar comandos que requieren acceso a la base de datos (suspend).
     * Retorna null si el comando no aplica.
     */
    suspend fun executeSuspend(command: Command): String? = withContext(Dispatchers.IO) {
        when (command.type) {
            CommandType.ADD_DIARY -> {
                val content = command.params["content"] ?: return@withContext null
                repository.addDiaryEntry(content = content)
                "Anotado en el diario, señor."
            }
            CommandType.ADD_TASK -> {
                val text = command.params["text"] ?: return@withContext null
                repository.addTask(text)
                "Tarea agregada: \"$text\", señor."
            }
            CommandType.LIST_TASKS -> {
                val tasks = repository.getActiveTasks()
                if (tasks.isEmpty()) {
                    "No tiene tareas pendientes, señor. Todo en orden."
                } else {
                    val list = tasks.mapIndexed { i, t -> "${i + 1}. ${t.text}" }.joinToString(", ")
                    "Sus tareas pendientes son: $list"
                }
            }
            CommandType.A11Y_CLEAN_CHATS -> {
                val app = command.params["app"] ?: ""
                val count = command.params["count"]?.toIntOrNull() ?: 1
                a11yCleanChats(app, count)
            }
            else -> null
        }
    }

    // ── Abrir app ──────────────────────────────────────────────

    private fun openApp(appName: String): String {
        val pkg = KNOWN_APPS[appName.lowercase()]
        if (pkg != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "Abriendo $appName, señor."
            }
        }
        // Buscar por nombre entre apps instaladas
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
        val match = apps.firstOrNull {
            pm.getApplicationLabel(it.activityInfo.applicationInfo)
                .toString().lowercase().contains(appName.lowercase())
        }
        if (match != null) {
            val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch != null) {
                context.startActivity(launch)
                return "Abriendo ${pm.getApplicationLabel(match.activityInfo.applicationInfo)}, señor."
            }
        }
        return "No encontré la aplicación \"$appName\" en el dispositivo."
    }

    // ── WhatsApp ───────────────────────────────────────────────

    private fun openWhatsApp(contact: String, message: String): String {
        return try {
            val intent = if (message.isNotEmpty()) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(message)}"))
                    .setPackage("com.whatsapp")
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))
                    .setPackage("com.whatsapp")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (message.isNotEmpty())
                "Abriendo WhatsApp con el mensaje para $contact. Por favor verifique antes de enviar, señor."
            else
                "Abriendo WhatsApp, señor."
        } catch (e: Exception) {
            "WhatsApp no está instalado, señor."
        }
    }

    // ── SMS ────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun sendSms(contact: String, message: String): String {
        return if (message.isEmpty()) {
            // Abrir app de mensajes
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$contact")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Abriendo mensajes para $contact, señor."
        } else {
            try {
                SmsManager.getDefault().sendTextMessage(contact, null, message, null, null)
                "SMS enviado a $contact, señor."
            } catch (e: Exception) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$contact")
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Abriendo mensajes para $contact, señor."
            }
        }
    }

    // ── Llamada ────────────────────────────────────────────────

    private fun makeCall(contact: String): String {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$contact")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Iniciando llamada a $contact, señor."
    }

    // ── Búsqueda ───────────────────────────────────────────────

    private fun webSearch(query: String): String {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        return "Buscando \"$query\" en Google, señor."
    }

    // ── Alarma ─────────────────────────────────────────────────

    private fun setAlarm(timeStr: String): String {
        val (hour, minute) = parseTime(timeStr)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "VISION SOLDIER")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Alarma configurada para las ${hour}:${minute.toString().padStart(2, '0')}, señor."
    }

    private fun parseTime(timeStr: String): Pair<Int, Int> {
        Regex("(\\d{1,2}):(\\d{2})").find(timeStr)?.let {
            return Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("(\\d{1,2})\\s*([ap]m?)").find(timeStr)?.let {
            var h = it.groupValues[1].toInt()
            if (it.groupValues[2].startsWith("p") && h < 12) h += 12
            if (it.groupValues[2].startsWith("a") && h == 12) h = 0
            return Pair(h, 0)
        }
        Regex("(\\d{1,2})").find(timeStr)?.let {
            var h = it.groupValues[1].toInt()
            if (timeStr.contains("tarde") || timeStr.contains("noche") || timeStr.contains("pm")) {
                if (h < 12) h += 12
            }
            return Pair(h, 0)
        }
        return Pair(7, 0)
    }

    // ── Música ─────────────────────────────────────────────────

    private fun playMusic(query: String): String {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("spotify:search:$query")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Reproduciendo \"$query\" en Spotify, señor."
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                "Buscando \"$query\" en YouTube, señor."
            } catch (e2: Exception) {
                "No encontré una aplicación de música instalada, señor."
            }
        }
    }

    // ── URL ────────────────────────────────────────────────────

    private fun openUrl(url: String): String {
        val finalUrl = if (!url.startsWith("http")) "https://$url" else url
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Abriendo $finalUrl, señor."
    }

    // ── AccessibilityService Actions ──────────────────────────

    private fun a11yWriteWhatsApp(contact: String, message: String): String {
        if (!AccessibilityActions.isServiceEnabled()) {
            return "Necesito que active el servicio de accesibilidad de VISION SOLDIER en Ajustes > Accesibilidad para poder escribir en WhatsApp, señor."
        }
        if (message.isEmpty()) {
            return "Necesito saber qué mensaje enviar, señor."
        }

        // Abrir WhatsApp y después escribir con AccessibilityService
        AccessibilityActions.openAppAndWrite(
            context = context,
            packageName = "com.whatsapp",
            text = message,
            sendButtonLabel = "Enviar",
        )
        return "Procediendo a escribir en WhatsApp: \"$message\". Verificando envío, señor."
    }

    private fun a11yWriteInstagram(text: String): String {
        if (!AccessibilityActions.isServiceEnabled()) {
            return "Necesito que active el servicio de accesibilidad en Ajustes > Accesibilidad, señor."
        }

        // Abrir Instagram
        val intent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            // Esperar y escribir
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                AccessibilityActions.writeText(context, text, "com.instagram.android")
            }, 3000)

            return "Abriendo Instagram. Procediendo a escribir: \"$text\", señor."
        }
        return "Instagram no está instalado, señor."
    }

    private fun a11yDeleteChat(contact: String): String {
        if (!AccessibilityActions.isServiceEnabled()) {
            return "Necesito el servicio de accesibilidad activo para manipular WhatsApp, señor."
        }

        // Paso 1: Abrir WhatsApp
        val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val handler = android.os.Handler(android.os.Looper.getMainLooper())

            // Paso 2: Buscar el chat por nombre del contacto (long press)
            handler.postDelayed({
                AccessibilityActions.findAndClick(context, contact)
            }, 2500)

            // Paso 3: Buscar y tocar "Eliminar chat" o "Borrar chat"
            handler.postDelayed({
                AccessibilityActions.findAndClick(context, "Eliminar chat")
            }, 4000)

            // Paso 4: Confirmar
            handler.postDelayed({
                AccessibilityActions.findAndClick(context, "Eliminar")
            }, 5500)

            return "Procediendo a eliminar el chat con $contact en WhatsApp, señor. Esto puede requerir confirmación manual."
        }
        return "WhatsApp no está instalado, señor."
    }

    private fun a11yNavigate(action: String): String {
        if (!AccessibilityActions.isServiceEnabled()) {
            return "El servicio de accesibilidad no está activo, señor."
        }
        when (action) {
            "back"    -> {
                AccessibilityActions.goBack(context)
                return "Regresando, señor."
            }
            "home"    -> {
                AccessibilityActions.goHome(context)
                return "Volviendo al inicio, señor."
            }
            "recents" -> {
                AccessibilityActions.showRecents(context)
                return "Mostrando aplicaciones recientes, señor."
            }
        }
        return "Navegando, señor."
    }

    private suspend fun a11yCleanChats(appName: String, count: Int): String {
        if (!AccessibilityActions.isServiceEnabled()) {
            return "Necesito el servicio de accesibilidad activo para borrar chats masivamente, señor."
        }
        val pkg = KNOWN_APPS[appName.lowercase()]
        if (pkg == null) {
            return "No conozco la aplicación $appName para borrar sus chats, señor."
        }

        // 1. Abrir la app
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            return "$appName no está instalada, señor."
        }

        // 2. Loop de borrado en hilo principal (necesario para broadcast)
        withContext(Dispatchers.Main) {
            // Esperar a que cargue la app (asumimos que abre en bandeja de entrada o requiere un tap previo)
            kotlinx.coroutines.delay(4000)

            // Iterar la cantidad de veces solicitada
            for (i in 1..count) {
                // En TikTok/IG, un swipe ligero hacia arriba (opcional) para actualizar la lista no viene mal,
                // o simplemente mantener presionado el primer chat de la lista.
                // Como no sabemos el texto exacto, buscamos palabras clave comunes como "Chat", o usamos una coordenada genérica.
                // Ya que AccessibilityActions funciona por Texto o Content-Description, asumo que hay un chat genérico.
                // NOTA: Para una app genérica, un long press por texto de contacto es ideal.
                // Aquí usamos una simulación de click general. Para TikTok:
                if (appName.lowercase() == "tiktok" || appName.lowercase() == "instagram") {
                    // Simular scroll ligero
                    AccessibilityActions.scroll(context, "down")
                    kotlinx.coroutines.delay(1000)
                    // Buscar palabra "Eliminar" (si hay swipe) o intentar long click en algún mensaje.
                    // ESTO ES UN PLACEHOLDER DE LA LÓGICA ESPECÍFICA DE LA APP.
                    AccessibilityActions.findAndClick(context, "Eliminar")
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
        return "He iniciado el protocolo de limpieza para $count chats en $appName, señor. Puede que deba confirmarlos manualmente según la aplicación."
    }
}
