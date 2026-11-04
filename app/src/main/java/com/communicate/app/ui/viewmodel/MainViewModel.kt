package com.communicate.app.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.communicate.app.audio.AudioEngineManager
import com.communicate.app.data.model.AudioMode
import com.communicate.app.data.model.BluetoothScoState
import com.communicate.app.data.model.ConnectionParams
import com.communicate.app.data.model.ConnectionStatus
import com.communicate.app.data.model.LocalRiderState
import com.communicate.app.data.model.PeerRiderState
import com.communicate.app.location.LocationEngineManager
import com.communicate.app.network.P2PConnectionManager
import com.communicate.app.network.QRCodeManager
import com.communicate.app.service.RiderForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"

    val p2pManager = P2PConnectionManager(application)
    val audioEngineManager = AudioEngineManager(application)
    val locationEngineManager = LocationEngineManager(application)

    val connectionStatus: StateFlow<ConnectionStatus> = p2pManager.connectionStatus
    val peerState: StateFlow<PeerRiderState> = p2pManager.peerState

    private val _localState = MutableStateFlow(LocalRiderState(deviceName = getDeviceName()))
    val localState: StateFlow<LocalRiderState> = _localState.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

    private val _connectionParams = MutableStateFlow<ConnectionParams?>(null)
    val connectionParams: StateFlow<ConnectionParams?> = _connectionParams.asStateFlow()

    init {
        locationEngineManager.setDeviceName(_localState.value.deviceName)
        startLocationTrackingIfPermitted()

        // 1. Forward outgoing PCM audio chunks from mic over P2P network (on IO Thread)
        viewModelScope.launch(Dispatchers.IO) {
            audioEngineManager.outgoingAudioFlow.collect { audioBytes ->
                if (p2pManager.connectionStatus.value == ConnectionStatus.CONNECTED) {
                    p2pManager.sendAudioChunk(audioBytes)
                }
            }
        }

        // 2. Feed incoming PCM audio payload from peer device into AudioTrack speaker (on IO Thread)
        viewModelScope.launch(Dispatchers.IO) {
            p2pManager.audioIncomingFlow.collect { audioBytes ->
                audioEngineManager.playIncomingAudio(audioBytes)
            }
        }

        // 3. Forward location telemetry packets to peer over P2P network
        viewModelScope.launch {
            locationEngineManager.telemetryPacket.collect { packet ->
                packet?.let {
                    _localState.value = _localState.value.copy(
                        speedMph = it.speedMph,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        bearing = it.bearing
                    )
                    if (p2pManager.connectionStatus.value == ConnectionStatus.CONNECTED) {
                        p2pManager.sendTelemetry(it)
                    }
                }
            }
        }

        // 4. Listen to audio engine state updates
        viewModelScope.launch {
            audioEngineManager.isMicMuted.collect { muted ->
                _localState.value = _localState.value.copy(isMicMuted = muted)
            }
        }

        viewModelScope.launch {
            audioEngineManager.isPttPressed.collect { pressed ->
                _localState.value = _localState.value.copy(isPttPressed = pressed)
            }
        }

        viewModelScope.launch {
            audioEngineManager.audioMode.collect { mode ->
                _localState.value = _localState.value.copy(audioMode = mode)
            }
        }

        viewModelScope.launch {
            audioEngineManager.bluetoothScoState.collect { scoState ->
                _localState.value = _localState.value.copy(
                    bluetoothScoState = scoState,
                    isBluetoothScoActive = scoState == BluetoothScoState.CONNECTED
                )
            }
        }

        viewModelScope.launch {
            audioEngineManager.isSpeakerphoneOn.collect { isSpeaker ->
                _localState.value = _localState.value.copy(isSpeakerphoneOn = isSpeaker)
            }
        }

        // 5. Connection state changes trigger audio and foreground service
        viewModelScope.launch {
            connectionStatus.collectLatest { status ->
                Log.d(TAG, "Connection status changed: $status")
                if (status == ConnectionStatus.CONNECTED) {
                    try {
                        RiderForegroundService.startService(getApplication())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting RiderForegroundService", e)
                    }
                    audioEngineManager.startAudioEngine()
                    startLocationTrackingIfPermitted()
                } else if (status == ConnectionStatus.DISCONNECTED) {
                    audioEngineManager.stopAudioEngine()
                    try {
                        RiderForegroundService.stopService(getApplication())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping RiderForegroundService", e)
                    }
                }
            }
        }
    }

    fun startLocationTrackingIfPermitted() {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            locationEngineManager.startLocationUpdates()
        }
    }

    /**
     * Generates a new connection QR code and starts P2P advertising.
     */
    fun createConnectionQRCode(): Bitmap? {
        val params = ConnectionParams(deviceName = _localState.value.deviceName)
        _connectionParams.value = params
        val bitmap = QRCodeManager.generateQRCode(params)
        _qrCodeBitmap.value = bitmap

        // Start advertising as host
        p2pManager.startAdvertising(params.deviceName, params.authCode)
        return bitmap
    }

    /**
     * Connects to a peer by scanning their QR code.
     */
    fun connectFromScannedCode(rawQrCodeText: String) {
        val params = QRCodeManager.parseQRCode(rawQrCodeText) ?: return
        _connectionParams.value = params
        p2pManager.startDiscoveryAndConnect(params, _localState.value.deviceName)
    }

    fun cancelAdvertising() {
        p2pManager.cancelAdvertising()
    }

    fun cancelDiscovery() {
        p2pManager.cancelDiscovery()
    }

    fun setAudioMode(mode: AudioMode) {
        _localState.value = _localState.value.copy(audioMode = mode)
        audioEngineManager.setAudioMode(mode)
    }

    fun toggleMute() {
        audioEngineManager.toggleMute()
    }

    fun setPttPressed(pressed: Boolean) {
        _localState.value = _localState.value.copy(isPttPressed = pressed)
        audioEngineManager.setPttState(pressed)
    }

    fun toggleAudioOutput() {
        audioEngineManager.toggleAudioOutput()
    }

    fun disconnect() {
        p2pManager.disconnect()
        audioEngineManager.stopAudioEngine()
        try {
            RiderForegroundService.stopService(getApplication())
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping RiderForegroundService in disconnect", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        p2pManager.cleanup()
        audioEngineManager.cleanup()
        locationEngineManager.stopLocationUpdates()
        try {
            RiderForegroundService.stopService(getApplication())
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping RiderForegroundService in onCleared", e)
        }
    }

    private fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer)) model else "$manufacturer $model"
    }
}
