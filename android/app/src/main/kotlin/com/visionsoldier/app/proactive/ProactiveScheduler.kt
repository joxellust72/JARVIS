package com.visionsoldier.app.proactive

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.visionsoldier.app.ai.GeminiClient
import com.visionsoldier.app.data.VisionRepository
import com.visionsoldier.app.service.BubbleOverlayService
import com.visionsoldier.app.service.VisionForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker que genera un comentario proactivo usando Gemini API,
 * lo cachea en Room y lo entrega al BubbleOverlayService.
 *
 * Tipos de comentario que rota:
 * - curiosity    → dato curioso, científico o histórico
 * - news         → tema del día o evento interesante
 * - english_word → palabra en inglés con traducción
 * - debate       → tema filosófico / debate intelectual (solo a las 12:15 PM)
 */
class ProactiveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveWorker"
        const val KEY_TYPE = "comment_type"

        private val REGULAR_TYPES = listOf("curiosity", "news", "english_word")
        private var lastTypeIndex = -1

        fun getNextType(): String {
            val available = REGULAR_TYPES.filterIndexed { i, _ -> i != lastTypeIndex }
            val pick = available.random()
            lastTypeIndex = REGULAR_TYPES.indexOf(pick)
            return pick
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val type = inputData.getString(KEY_TYPE) ?: getNextType()
            val repo = VisionRepository.getInstance(applicationContext)

            // Revisar caché — no regenerar el mismo tipo en el mismo día
            val cached = repo.getCachedComment(type)
            val (title, text) = if (cached != null && cached.content.isNotEmpty()) {
                Pair(cached.title, cached.content)
            } else {
                val gemini = GeminiClient(VisionForegroundService.GEMINI_API_KEY)
                val pair = gemini.generateProactiveComment(type)
                if (pair.second.isNotEmpty()) {
                    repo.cacheComment(type, pair.first, pair.second)
                }
                pair
            }

            if (text.isNotEmpty()) {
                // Enviar al BubbleOverlayService para mostrar en la burbuja
                val intent = Intent(applicationContext, BubbleOverlayService::class.java).apply {
                    action = BubbleOverlayService.ACTION_UPDATE
                    putExtra(BubbleOverlayService.EXTRA_TITLE, title)
                    putExtra(BubbleOverlayService.EXTRA_TEXT, text)
                }
                try {
                    applicationContext.startService(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo enviar a BubbleService: ${e.message}")
                }

                // También broadcast para que VisionForegroundService lo hable si la app está activa
                val broadcast = Intent("vision.PROACTIVE_COMMENT").apply {
                    putExtra("title", title)
                    putExtra("text", text)
                    putExtra("type", type)
                }
                applicationContext.sendBroadcast(broadcast)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error generando comentario proactivo: ${e.message}")
            Result.retry()
        }
    }
}

/**
 * Programador de comentarios proactivos.
 * Usa WorkManager para programar periódicamente la generación de comentarios.
 */
object ProactiveScheduler {

    private const val WORK_REGULAR = "vision_proactive_regular"
    private const val WORK_DEBATE  = "vision_proactive_debate"

    /**
     * Inicia el loop de comentarios proactivos regulares (cada 15 minutos).
     * Se ejecuta como PeriodicWork — sobrevive a reinicios de la app.
     */
    fun startRegularLoop(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val regularWork = PeriodicWorkRequestBuilder<ProactiveWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.MINUTES) // Primer comentario a los 2 min
            .addTag(WORK_REGULAR)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_REGULAR,
            ExistingPeriodicWorkPolicy.KEEP,
            regularWork,
        )
        Log.d("ProactiveScheduler", "Loop regular programado (cada 15 min)")
    }

    /**
     * Programa el debate de las 12:15 PM como OneTimeWork.
     * Se re-programa cada día desde el servicio.
     */
    fun scheduleDebate(context: Context) {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 15)
            set(java.util.Calendar.SECOND, 0)
        }

        // Si ya pasó la hora hoy, programar para mañana
        if (now.after(target)) {
            target.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        val delayMs = target.timeInMillis - now.timeInMillis

        val debateWork = OneTimeWorkRequestBuilder<ProactiveWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ProactiveWorker.KEY_TYPE to "debate"))
            .addTag(WORK_DEBATE)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_DEBATE,
            ExistingWorkPolicy.REPLACE,
            debateWork,
        )
        Log.d("ProactiveScheduler", "Debate programado en ${delayMs / 1000 / 60} min")
    }

    /** Dispara un comentario inmediato (para cuando el usuario minimiza la app) */
    fun triggerNow(context: Context, type: String? = null) {
        val data = if (type != null) {
            workDataOf(ProactiveWorker.KEY_TYPE to type)
        } else {
            Data.EMPTY
        }

        val work = OneTimeWorkRequestBuilder<ProactiveWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_REGULAR)
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_DEBATE)
    }
}
