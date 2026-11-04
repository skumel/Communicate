package com.communicate.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.communicate.app.ui.screens.DashboardScreen
import com.communicate.app.ui.screens.SplashScreen
import com.communicate.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.startLocationTrackingIfPermitted()
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Permissions (Camera, Audio, Location, Bluetooth) are required for offline bike comms.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppPermissions()

        setContent {
            var showSplashScreen by rememberSaveable { mutableStateOf(true) }

            val localState by viewModel.localState.collectAsStateWithLifecycle()
            val peerState by viewModel.peerState.collectAsStateWithLifecycle()
            val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
            val qrBitmap by viewModel.qrCodeBitmap.collectAsStateWithLifecycle()

            Crossfade(
                targetState = showSplashScreen,
                animationSpec = tween(durationMillis = 500),
                label = "ScreenTransition"
            ) { isSplash ->
                if (isSplash) {
                    SplashScreen(
                        onInitializationComplete = {
                            showSplashScreen = false
                        }
                    )
                } else {
                    DashboardScreen(
                        localState = localState,
                        peerState = peerState,
                        connectionStatus = connectionStatus,
                        qrBitmap = qrBitmap,
                        onGenerateQrCode = { viewModel.createConnectionQRCode() },
                        onCodeScanned = { rawCode -> viewModel.connectFromScannedCode(rawCode) },
                        onAudioModeChanged = { mode -> viewModel.setAudioMode(mode) },
                        onToggleMute = { viewModel.toggleMute() },
                        onPttPressedChange = { pressed -> viewModel.setPttPressed(pressed) },
                        onToggleAudioOutput = { viewModel.toggleAudioOutput() },
                        onCancelAdvertising = { viewModel.cancelAdvertising() },
                        onCancelScan = { viewModel.cancelDiscovery() },
                        onDisconnect = { viewModel.disconnect() },
                        onRequestPermissions = { requestAppPermissions() }
                    )
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
