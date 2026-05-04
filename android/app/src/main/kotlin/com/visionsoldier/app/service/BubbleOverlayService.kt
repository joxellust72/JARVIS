package com.visionsoldier.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.visionsoldier.app.MainActivity
import com.visionsoldier.app.proactive.ProactiveScheduler

/**
 * Servicio de Burbuja Flotante real — flota sobre cualquier app.
 * Requiere permiso SYSTEM_ALERT_WINDOW (Settings > Apps > VISION > Mostrar sobre otras apps)
 *
 * Ahora integrado con ProactiveScheduler:
 * - Al mostrarse, dispara un comentario inmediato
 * - Recibe comentarios proactivos y los muestra en un tooltip
 */
class BubbleOverlayService : Service() {

    companion object {
        const val ACTION_SHOW   = "vision.bubble.SHOW"
        const val ACTION_HIDE   = "vision.bubble.HIDE"
        const val ACTION_UPDATE = "vision.bubble.UPDATE"
        const val EXTRA_TEXT    = "text"
        const val EXTRA_TITLE   = "title"
    }

    inner class BubbleBinder : Binder() {
        fun getService(): BubbleOverlayService = this@BubbleOverlayService
    }

    private val binder = BubbleBinder()
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var speechView: View? = null
    private var isShowing = false

    private var startX = 0f
    private var startY = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW   -> {
                show()
                // Disparar primer comentario al mostrar la burbuja
                ProactiveScheduler.triggerNow(this)
            }
            ACTION_HIDE   -> hide()
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "VISION"
                val text  = intent.getStringExtra(EXTRA_TEXT)  ?: ""
                updateSpeech(title, text)
            }
        }
        return START_NOT_STICKY
    }

    // ── Mostrar burbuja ────────────────────────────────────────

    fun show() {
        if (isShowing) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val wm = windowManager ?: return

        // Container principal de la burbuja — orbe JARVIS circular
        val container = FrameLayout(this).apply {
            // Core orb con gradiente azul JARVIS
            val orbOuter = View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(2, 0x5000E5FF.toInt())
                    setColor(0x20000000)
                }
            }
            addView(orbOuter, FrameLayout.LayoutParams(60.dpToPx(), 60.dpToPx()).apply {
                gravity = Gravity.CENTER
            })

            val orbCore = View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    colors = intArrayOf(0xFF00E5FF.toInt(), 0xFF0040A0.toInt())
                    gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = 45f
                }
                alpha = 0.85f
            }
            addView(orbCore, FrameLayout.LayoutParams(42.dpToPx(), 42.dpToPx()).apply {
                gravity = Gravity.CENTER
            })

            // Pulse animation
            orbOuter.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .setDuration(1500)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction {
                    orbOuter.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(1500)
                        .withEndAction { orbOuter.performClick() }
                        .start()
                }
                .start()
        }

        val params = WindowManager.LayoutParams(
            76.dpToPx(),
            76.dpToPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16.dpToPx()
            y = 200.dpToPx()
        }

        container.setOnTouchListener { view, event ->
            handleTouch(view, event, params)
            true
        }

        container.setOnClickListener {
            if (!isDragging) {
                // Abrir app al tocar
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(launchIntent)
            }
        }

        wm.addView(container, params)
        bubbleView = container
        isShowing = true
    }

    // ── Ocultar burbuja ────────────────────────────────────────

    fun hide() {
        if (!isShowing) return
        bubbleView?.let { windowManager?.removeView(it) }
        speechView?.let { windowManager?.removeView(it) }
        bubbleView = null
        speechView = null
        isShowing = false
    }

    // ── Mostrar texto de la burbuja (comentario proactivo) ─────

    fun updateSpeech(title: String, text: String) {
        if (!isShowing) show()

        speechView?.let { windowManager?.removeView(it) }

        // Card con estilo JARVIS
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE6050A14.toInt())
                cornerRadius = 12f
                setStroke(1, 0x4000E5FF.toInt())
            }
            elevation = 8f

            // Título
            addView(TextView(context).apply {
                this.text = title
                setTextColor(0xFF00E5FF.toInt())
                textSize = 10f
                letterSpacing = 0.15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })

            // Contenido
            addView(TextView(context).apply {
                this.text = text
                setTextColor(0xFFE0F7FA.toInt())
                textSize = 13f
                maxWidth = 260.dpToPx()
                setPadding(0, 4.dpToPx(), 0, 0)
            })
        }

        val speechParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16.dpToPx()
            y = 310.dpToPx()
        }

        windowManager?.addView(card, speechParams)
        speechView = card

        // Fade-in animation
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(400).start()

        // Auto-remove after 12 seconds with fade-out
        card.postDelayed({
            if (speechView == card) {
                card.animate().alpha(0f).setDuration(500).withEndAction {
                    try {
                        windowManager?.removeView(card)
                    } catch (_: Exception) {}
                    if (speechView == card) speechView = null
                }.start()
            }
        }, 12_000)
    }

    // ── Arrastrar burbuja ──────────────────────────────────────

    private var lastClickTime: Long = 0

    private fun handleTouch(view: View, event: MotionEvent, params: WindowManager.LayoutParams) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = params.x.toFloat()
                startY = params.y.toFloat()
                startTouchX = event.rawX
                startTouchY = event.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startTouchX
                val dy = event.rawY - startTouchY
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true
                    // Como gravity es TOP | END, el eje X está invertido respecto a rawX
                    params.x = (startX - dx).toInt()
                    params.y = (startY + dy).toInt()
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        // View might be detached
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastClickTime < 300) {
                        // Doble toque -> ocultar burbuja
                        hide()
                    } else {
                        view.performClick()
                    }
                    lastClickTime = clickTime
                }
                isDragging = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hide()
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()
}
