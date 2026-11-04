package com.communicate.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.communicate.app.data.model.AudioMode
import com.communicate.app.data.model.BluetoothScoState
import com.communicate.app.data.model.ConnectionStatus
import com.communicate.app.data.model.LocalRiderState
import com.communicate.app.data.model.PeerRiderState
import com.communicate.app.ui.components.AudioWaveformVisualizer
import com.communicate.app.ui.components.QRCodeDialog
import com.communicate.app.ui.components.QRScannerView
import com.communicate.app.ui.components.RiderMapWidget
import com.communicate.app.ui.components.SpeedometerWidget
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DashboardScreen(
    localState: LocalRiderState,
    peerState: PeerRiderState,
    connectionStatus: ConnectionStatus,
    qrBitmap: Bitmap?,
    onGenerateQrCode: () -> Unit,
    onCodeScanned: (String) -> Unit,
    onAudioModeChanged: (AudioMode) -> Unit,
    onToggleMute: () -> Unit,
    onPttPressedChange: (Boolean) -> Unit,
    onToggleAudioOutput: () -> Unit,
    onCancelAdvertising: () -> Unit = {},
    onCancelScan: () -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    var showQrDialog by remember { mutableStateOf(false) }
    var isScanningQr by remember { mutableStateOf(false) }
    var currentTimeString by remember { mutableStateOf(getCurrentTimeString()) }

    // Auto-close QR dialog once a peer connects or starts authenticating
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED ||
            connectionStatus == ConnectionStatus.AUTHENTICATING
        ) {
            showQrDialog = false
        }
    }

    // Update Digital Clock every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = getCurrentTimeString()
            delay(1000L)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF020617) // Ultra-dark slate background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            val context = LocalContext.current

            val haptic = LocalHapticFeedback.current

            // CameraX Scanner sheet overlay if scanning
            if (isScanningQr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                ) {
                    QRScannerView(
                        onCodeScanned = { code ->
                            isScanningQr = false
                            onCodeScanned(code)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = {
                            isScanningQr = false
                            onCancelScan()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Scanner", tint = Color.White)
                    }
                }
            } else {
                // TOP SECTION: Speedometer & Radar Map View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Real-time GPS Speedometer Widget
                    SpeedometerWidget(
                        speedMph = localState.speedMph,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    // Offline Dual Rider Radar Map Widget
                    RiderMapWidget(
                        localLat = localState.latitude,
                        localLng = localState.longitude,
                        localBearing = localState.bearing,
                        peerState = peerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // MIDDLE BAR: Speaker Choice Button, 12-Hour Clock, and QR Connect Buttons
                TopStatusBar(
                    currentTime = currentTimeString,
                    connectionStatus = connectionStatus,
                    bluetoothState = localState.bluetoothScoState,
                    isSpeakerphone = localState.isSpeakerphoneOn,
                    onToggleAudioOutput = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleAudioOutput()
                    },
                    onShowQr = {
                        if (hasNearbyPermissions(context)) {
                            onGenerateQrCode()
                            showQrDialog = true
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Location and Bluetooth/Wi-Fi permissions are required to host P2P connection.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onRequestPermissions()
                        }
                    },
                    onStartScan = {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasCameraPermission && hasNearbyPermissions(context)) {
                            isScanningQr = true
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Camera, Location, and Bluetooth/Wi-Fi permissions are required to scan and connect.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onRequestPermissions()
                        }
                    },
                    onDisconnect = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDisconnect()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // BOTTOM SECTION: Voice Chat Mode Selector & High-Visibility Rider Controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Voice Mode Tabs (Push-To-Talk vs Open Mic)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E293B))
                                .padding(4.dp)
                        ) {
                            VoiceModeTab(
                                text = "Push-To-Talk (PTT)",
                                selected = localState.audioMode == AudioMode.PUSH_TO_TALK,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAudioModeChanged(AudioMode.PUSH_TO_TALK)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            VoiceModeTab(
                                text = "Open Mic",
                                selected = localState.audioMode == AudioMode.OPEN_MIC,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAudioModeChanged(AudioMode.OPEN_MIC)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Huge High-Visibility Rider Action Target
                        if (localState.audioMode == AudioMode.PUSH_TO_TALK) {
                            DisposableEffect(Unit) {
                                onDispose {
                                    onPttPressedChange(false)
                                }
                            }

                            // Massive Push-To-Talk Hold Target
                            val pttActive = localState.isPttPressed
                            val buttonColor = if (pttActive) Color(0xFFEF4444) else Color(0xFF0284C7)
                            val buttonText = if (pttActive) "TRANSMITTING..." else "HOLD TO TALK"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(buttonColor)
                                    .pointerInteropFilter { motionEvent ->
                                        when (motionEvent.action) {
                                            MotionEvent.ACTION_DOWN -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onPttPressedChange(true)
                                                true
                                            }
                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onPttPressedChange(false)
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Mic",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = buttonText,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    if (pttActive) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AudioWaveformVisualizer(
                                            isActive = true,
                                            barCount = 6,
                                            activeColor = Color.White,
                                            modifier = Modifier
                                                .height(28.dp)
                                                .width(48.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Massive Open Mic Mute / Unmute Toggle Target
                            val isMuted = localState.isMicMuted
                            val toggleBg = if (isMuted) Color(0xFF334155) else Color(0xFF10B981)
                            val toggleText = if (isMuted) "MIC MUTED (TAP TO UNMUTE)" else "MIC LIVE (TAP TO MUTE)"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(toggleBg)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleMute()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mute Toggle",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = toggleText,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    if (!isMuted) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AudioWaveformVisualizer(
                                            isActive = true,
                                            barCount = 6,
                                            activeColor = Color.White,
                                            modifier = Modifier
                                                .height(28.dp)
                                                .width(48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Display QR Code Dialog
        if (showQrDialog) {
            QRCodeDialog(
                qrBitmap = qrBitmap,
                deviceName = localState.deviceName,
                onDismiss = {
                    showQrDialog = false
                    onCancelAdvertising()
                }
            )
        }
    }
}

@Composable
private fun TopStatusBar(
    currentTime: String,
    connectionStatus: ConnectionStatus,
    bluetoothState: BluetoothScoState,
    isSpeakerphone: Boolean,
    onToggleAudioOutput: () -> Unit,
    onShowQr: () -> Unit,
    onStartScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0F172A))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // LEFT: Audio Output Switch Button (Loudspeaker vs BT Headset)
        val isBtActive = bluetoothState == BluetoothScoState.CONNECTED && !isSpeakerphone
        val activeColor = if (isBtActive) Color(0xFF10B981) else Color(0xFF06B6D4)
        val icon = if (isBtActive) Icons.Default.BluetoothConnected else Icons.AutoMirrored.Filled.VolumeUp
        val label = if (isBtActive) "BT AUDIO" else "SPEAKER"

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(activeColor.copy(alpha = 0.2f))
                .border(1.dp, activeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .clickable { onToggleAudioOutput() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Audio Output Toggle",
                tint = activeColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = activeColor
            )
        }

        // CENTER: Prominent 12-Hour Digital Clock Display
        Text(
            text = currentTime,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF38BDF8)
        )

        // RIGHT: Connection Status Badge & Pairing Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val statusBg = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> Color(0xFF10B981)
                ConnectionStatus.CONNECTING, ConnectionStatus.ADVERTISING, ConnectionStatus.DISCOVERING, ConnectionStatus.AUTHENTICATING -> Color(0xFFF59E0B)
                ConnectionStatus.DISCONNECTED -> Color(0xFF64748B)
            }
            val statusText = when (connectionStatus) {
                ConnectionStatus.DISCONNECTED -> "OFFLINE"
                ConnectionStatus.AUTHENTICATING -> "VERIFYING"
                else -> connectionStatus.name
            }
            Text(
                text = statusText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusBg)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            if (connectionStatus == ConnectionStatus.CONNECTED) {
                IconButton(onClick = onDisconnect, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Disconnect", tint = Color(0xFFEF4444))
                }
            } else {
                IconButton(onClick = onShowQr, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = Color(0xFF38BDF8))
                }
                IconButton(onClick = onStartScan, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = Color(0xFF10B981))
                }
            }
        }
    }
}

@Composable
private fun VoiceModeTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) Color(0xFF0284C7) else Color.Transparent
    val textColor = if (selected) Color.White else Color(0xFF94A3B8)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private fun hasNearbyPermissions(context: Context): Boolean {
    val hasLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasLocation) return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasBt = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasBt) return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasNearbyWifi = ContextCompat.checkSelfPermission(
            context, Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasNearbyWifi) return false
    }

    return true
}

private fun getCurrentTimeString(): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.getDefault())
    return LocalTime.now().format(formatter)
}
