package com.visionsoldier.app.accessibility

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Helper para ejecutar acciones de AccessibilityService desde cualquier parte de la app.
 * Envía broadcasts al VisionAccessibilityService.
 *
 * Uso:
 *   AccessibilityActions.writeText(context, "Hola mundo", "com.whatsapp")
 *   AccessibilityActions.clickOnText(context, "Enviar")
 *   AccessibilityActions.goBack(context)
 */
object AccessibilityActions {

    private const val TAG = "A11yActions"

    /** Verificar si el servicio de accesibilidad está activo */
    fun isServiceEnabled(): Boolean = VisionAccessibilityService.isRunning

    /**
     * Escribir texto en el campo de entrada activo de una app.
     * @param text texto a escribir
     * @param packageName paquete de la app objetivo (opcional, para filtrar)
     */
    fun writeText(context: Context, text: String, packageName: String? = null) {
        if (!isServiceEnabled()) {
            Log.w(TAG, "AccessibilityService no está activo")
            return
        }
        val intent = Intent(VisionAccessibilityService.ACTION_WRITE_TEXT).apply {
            putExtra(VisionAccessibilityService.EXTRA_TEXT, text)
            packageName?.let { putExtra(VisionAccessibilityService.EXTRA_PACKAGE, it) }
        }
        context.sendBroadcast(intent)
    }

    /**
     * Hacer click en un elemento por su texto visible.
     * @param target texto del elemento a clickear (ej: "Enviar", "Aceptar")
     */
    fun clickOnText(context: Context, target: String) {
        if (!isServiceEnabled()) return
        val intent = Intent(VisionAccessibilityService.ACTION_CLICK).apply {
            putExtra(VisionAccessibilityService.EXTRA_TARGET, target)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Hacer Long Click (mantener presionado) en un elemento por su texto visible.
     */
    fun longClickOnText(context: Context, target: String) {
        if (!isServiceEnabled()) return
        val intent = Intent(VisionAccessibilityService.ACTION_LONG_CLICK).apply {
            putExtra(VisionAccessibilityService.EXTRA_TARGET, target)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Buscar un elemento por texto o content-description y hacer click.
     */
    fun findAndClick(context: Context, target: String) {
        if (!isServiceEnabled()) return
        val intent = Intent(VisionAccessibilityService.ACTION_FIND_AND_CLICK).apply {
            putExtra(VisionAccessibilityService.EXTRA_TARGET, target)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Hacer scroll en la pantalla actual.
     * @param direction "up" o "down"
     */
    fun scroll(context: Context, direction: String = "down") {
        if (!isServiceEnabled()) return
        val intent = Intent(VisionAccessibilityService.ACTION_SCROLL).apply {
            putExtra(VisionAccessibilityService.EXTRA_DIRECTION, direction)
        }
        context.sendBroadcast(intent)
    }

    /** Navegar hacia atrás */
    fun goBack(context: Context) {
        if (!isServiceEnabled()) return
        context.sendBroadcast(Intent(VisionAccessibilityService.ACTION_BACK))
    }

    /** Ir al home */
    fun goHome(context: Context) {
        if (!isServiceEnabled()) return
        context.sendBroadcast(Intent(VisionAccessibilityService.ACTION_HOME))
    }

    /** Ver apps recientes */
    fun showRecents(context: Context) {
        if (!isServiceEnabled()) return
        context.sendBroadcast(Intent(VisionAccessibilityService.ACTION_RECENTS))
    }

    /** Leer el contenido de la pantalla actual (el resultado llega por broadcast) */
    fun readScreen(context: Context) {
        if (!isServiceEnabled()) return
        context.sendBroadcast(Intent(VisionAccessibilityService.ACTION_READ_SCREEN))
    }

    /**
     * Secuencia completa: abrir app → esperar → escribir texto → click en enviar.
     * Útil para "manda mensaje a Mamá por WhatsApp: llegué"
     */
    fun openAppAndWrite(
        context: Context,
        packageName: String,
        text: String,
        sendButtonLabel: String = "Enviar",
        delayBeforeWrite: Long = 2000,
        delayBeforeSend: Long = 500,
    ) {
        // 1. Abrir la app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }

        // 2. Esperar a que se abra y escribir
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            writeText(context, text, packageName)

            // 3. Click en enviar después de escribir
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                findAndClick(context, sendButtonLabel)
            }, delayBeforeSend)
        }, delayBeforeWrite)
    }
}
