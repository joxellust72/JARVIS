package com.visionsoldier.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VisionApp : Application() {

    companion object {
        const val CHANNEL_ID_SERVICE = "vision_service_channel"
        const val CHANNEL_ID_ALERTS  = "vision_alerts_channel"
        lateinit var instance: VisionApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Canal para el servicio persistente
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "VISION SOLDIER Activo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de escucha continua de VISION SOLDIER"
                setShowBadge(false)
            }

            // Canal para alertas y comentarios proactivos
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "VISION — Comentarios",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Comentarios proactivos de VISION SOLDIER"
            }

            manager.createNotificationChannels(listOf(serviceChannel, alertChannel))
        }
    }
}
