package com.visionsoldier.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityService de VISION SOLDIER.
 *
 * Permite que la IA controle completamente la interfaz del dispositivo:
 * - Escribir texto en campos de cualquier app (WhatsApp, Instagram, etc.)
 * - Hacer click en botones / elementos
 * - Hacer scroll
 * - Navegar hacia atrás / home
 * - Leer contenido de la pantalla
 *
 * Se comunica con VisionForegroundService via broadcasts.
 */
class VisionAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VisionA11y"

        // Acciones que se pueden ejecutar
        const val ACTION_WRITE_TEXT   = "vision.a11y.WRITE_TEXT"
        const val ACTION_CLICK        = "vision.a11y.CLICK"
        const val ACTION_LONG_CLICK   = "vision.a11y.LONG_CLICK"
        const val ACTION_SCROLL       = "vision.a11y.SCROLL"
        const val ACTION_BACK         = "vision.a11y.BACK"
        const val ACTION_HOME         = "vision.a11y.HOME"
        const val ACTION_RECENTS      = "vision.a11y.RECENTS"
        const val ACTION_READ_SCREEN  = "vision.a11y.READ_SCREEN"
        const val ACTION_FIND_AND_CLICK = "vision.a11y.FIND_AND_CLICK"

        // Extras
        const val EXTRA_TEXT       = "text"
        const val EXTRA_TARGET     = "target"       // texto del nodo a buscar
        const val EXTRA_DIRECTION  = "direction"     // "up" o "down"
        const val EXTRA_PACKAGE    = "package_name"  // paquete de la app objetivo

        // Estado del servicio
        var isRunning = false
            private set

        var instance: VisionAccessibilityService? = null
            private set
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_WRITE_TEXT    -> handleWriteText(intent)
                ACTION_CLICK        -> handleClick(intent, isLongClick = false)
                ACTION_LONG_CLICK   -> handleClick(intent, isLongClick = true)
                ACTION_SCROLL       -> handleScroll(intent)
                ACTION_BACK         -> performGlobalAction(GLOBAL_ACTION_BACK)
                ACTION_HOME         -> performGlobalAction(GLOBAL_ACTION_HOME)
                ACTION_RECENTS      -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                ACTION_READ_SCREEN  -> handleReadScreen()
                ACTION_FIND_AND_CLICK -> handleFindAndClick(intent)
            }
        }
    }

    // ── Ciclo de vida ──────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true

        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        // Registrar receiver para recibir comandos
        val filter = IntentFilter().apply {
            addAction(ACTION_WRITE_TEXT)
            addAction(ACTION_CLICK)
            addAction(ACTION_LONG_CLICK)
            addAction(ACTION_SCROLL)
            addAction(ACTION_BACK)
            addAction(ACTION_HOME)
            addAction(ACTION_RECENTS)
            addAction(ACTION_READ_SCREEN)
            addAction(ACTION_FIND_AND_CLICK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        Log.d(TAG, "AccessibilityService conectado y listo")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Escuchar eventos de UI (se puede usar para tracking de estado futuro)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        Log.d(TAG, "AccessibilityService destruido")
    }

    // ── Escribir texto en un campo ────────────────────────────

    /**
     * Busca el campo de texto enfocado (o el primer editable) y escribe texto.
     * Funciona en WhatsApp, Instagram, Telegram, etc.
     */
    private fun handleWriteText(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val targetPkg = intent.getStringExtra(EXTRA_PACKAGE)

        val root = rootInActiveWindow ?: return
        val editableNode = findEditableNode(root, targetPkg)

        if (editableNode != null) {
            // Enfocar el nodo
            editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // Inyectar texto
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Texto escrito: '$text'")

            broadcastResult("Texto escrito correctamente, señor.")
        } else {
            Log.w(TAG, "No se encontró campo de texto editable")
            broadcastResult("No encontré un campo de texto en la pantalla actual, señor.")
        }
    }

    // ── Click en elemento por texto ───────────────────────────

    private fun handleClick(intent: Intent, isLongClick: Boolean = false) {
        val target = intent.getStringExtra(EXTRA_TARGET) ?: return
        val root = rootInActiveWindow ?: return
        val node = findNodeByText(root, target)

        if (node != null) {
            performClickOnNode(node, isLongClick)
            Log.d(TAG, "${if(isLongClick) "Long Click" else "Click"} ejecutado en: '$target'")
            broadcastResult("Hecho, señor.")
        } else {
            Log.w(TAG, "No se encontró nodo con texto: '$target'")
            broadcastResult("No encontré el elemento \"$target\" en la pantalla, señor.")
        }
    }

    // ── Buscar y hacer click ──────────────────────────────────

    private fun handleFindAndClick(intent: Intent) {
        val target = intent.getStringExtra(EXTRA_TARGET) ?: return
        val root = rootInActiveWindow ?: return
        val node = findNodeByText(root, target)
            ?: findNodeByContentDescription(root, target)

        if (node != null) {
            performClickOnNode(node)
            broadcastResult("Encontré y toqué \"$target\", señor.")
        } else {
            broadcastResult("No encontré \"$target\" en la pantalla, señor.")
        }
    }

    // ── Scroll ────────────────────────────────────────────────

    private fun handleScroll(intent: Intent) {
        val direction = intent.getStringExtra(EXTRA_DIRECTION) ?: "down"
        val root = rootInActiveWindow ?: return
        val scrollable = findScrollableNode(root)

        if (scrollable != null) {
            val action = if (direction == "up")
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            scrollable.performAction(action)
            Log.d(TAG, "Scroll $direction ejecutado")
        }
    }

    // ── Leer pantalla ─────────────────────────────────────────

    private fun handleReadScreen() {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectTexts(root, texts)

        val screenContent = texts.take(20).joinToString(" | ")
        Log.d(TAG, "Pantalla: $screenContent")

        val broadcast = Intent("vision.A11Y_SCREEN_CONTENT").apply {
            putExtra("content", screenContent)
        }
        sendBroadcast(broadcast)
    }

    // ── Utilidades de búsqueda ─────────────────────────────────

    private fun findEditableNode(node: AccessibilityNodeInfo, targetPkg: String? = null): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocusable) {
            if (targetPkg == null || node.packageName?.toString() == targetPkg) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNode(child, targetPkg)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = node.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull { it.isClickable || it.parent?.isClickable == true }
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByContentDescription(child, desc)
            if (result != null) return result
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo, isLongClick: Boolean = false) {
        val action = if (isLongClick) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        if (node.isClickable || (isLongClick && node.isLongClickable)) {
            node.performAction(action)
        } else {
            // Intentar en el padre
            node.parent?.let {
                if (it.isClickable || (isLongClick && it.isLongClickable)) {
                    it.performAction(action)
                }
            }
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, texts) }
        }
    }

    private fun broadcastResult(message: String) {
        sendBroadcast(Intent("vision.A11Y_RESULT").putExtra("message", message))
    }
}
