package com.visionsoldier.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.visionsoldier.app.accessibility.VisionAccessibilityService
import com.visionsoldier.app.service.BubbleOverlayService
import com.visionsoldier.app.service.VisionForegroundService
import com.visionsoldier.app.ui.screens.MainScreen
import com.visionsoldier.app.ui.theme.Palettes
import com.visionsoldier.app.ui.theme.VisionTheme
import com.visionsoldier.app.ui.viewmodel.MainViewModel
import com.visionsoldier.app.ui.viewmodel.OrbState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var visionService: VisionForegroundService? = null
    private var serviceConnected = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startVisionService()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as VisionForegroundService.VisionBinder
            visionService = binder.getService()
            serviceConnected = true

            visionService?.onWakeWordDetected = { command ->
                runOnUiThread { viewModel.onWakeWord(command) }
            }
            visionService?.onResponseReady = { response ->
                runOnUiThread { viewModel.onSpeaking(response) }
            }
            visionService?.onListening = {
                runOnUiThread { viewModel.onListening() }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceConnected = false
            visionService = null
        }
    }

    private val visionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra(VisionForegroundService.EXTRA_TEXT) ?: ""
            when (intent.action) {
                VisionForegroundService.ACTION_WAKE_WORD -> viewModel.onWakeWord(text)
                VisionForegroundService.ACTION_RESPONSE  -> viewModel.onSpeaking(text)
                VisionForegroundService.ACTION_LISTENING -> {
                    if (!serviceConnected || visionService?.isProcessing == false) {
                        viewModel.onListening()
                    }
                    if (text.isNotEmpty()) viewModel.setTranscript(text)
                }
                VisionForegroundService.ACTION_SPEAKING  -> viewModel.setOrbState(OrbState.SPEAKING)
                VisionForegroundService.ACTION_COMMAND   -> viewModel.onProcessing()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Habilitar edge-to-edge: la app ocupa toda la pantalla
        // incluido debajo de la barra de sistema y navegación
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsState()
            val palette = Palettes[state.colorPalette] ?: Palettes["jarvis"]!!

            VisionTheme(palette = palette) {
                MainScreen(
                    viewModel = viewModel,
                    onSendMessage = { text ->
                        viewModel.addMessage("user", text)
                        viewModel.onProcessing()
                        if (serviceConnected) {
                            visionService?.processInput(text)
                        }
                    },
                    onMinimize = {
                        viewModel.setMinimized(true)
                        if (Settings.canDrawOverlays(this)) {
                            startService(Intent(this, BubbleOverlayService::class.java).apply {
                                action = BubbleOverlayService.ACTION_SHOW
                            })
                        } else {
                            requestOverlayPermission()
                        }
                        moveTaskToBack(true)
                    }
                )
            }
        }

        requestPermissionsAndStart()
        registerVisionReceiver()
        checkAccessibilityService()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceConnected) unbindService(serviceConnection)
        unregisterReceiver(visionReceiver)
    }

    private fun requestPermissionsAndStart() {
        val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startVisionService()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startVisionService() {
        val intent = Intent(this, VisionForegroundService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun registerVisionReceiver() {
        val filter = IntentFilter().apply {
            addAction(VisionForegroundService.ACTION_WAKE_WORD)
            addAction(VisionForegroundService.ACTION_RESPONSE)
            addAction(VisionForegroundService.ACTION_LISTENING)
            addAction(VisionForegroundService.ACTION_SPEAKING)
            addAction(VisionForegroundService.ACTION_COMMAND)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(visionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(visionReceiver, filter)
        }
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun checkAccessibilityService() {
        if (!VisionAccessibilityService.isRunning) {
            Toast.makeText(
                this,
                "Activa VISION SOLDIER en Ajustes > Accesibilidad para control total",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
