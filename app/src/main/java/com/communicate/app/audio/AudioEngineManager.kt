package com.communicate.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.communicate.app.data.model.AudioMode
import com.communicate.app.data.model.BluetoothScoState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioEngineManager(private val context: Context) {

    private val TAG = "AudioEngineManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioMutex = Mutex()

    // Audio record/track settings
    private val SAMPLE_RATE = 16000
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val CHUNK_SIZE = 640 // 20ms of audio at 16kHz 16-bit mono

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null

    private val _outgoingAudioFlow = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val outgoingAudioFlow: SharedFlow<ByteArray> = _outgoingAudioFlow.asSharedFlow()

    private val _audioMode = MutableStateFlow(AudioMode.PUSH_TO_TALK)
    val audioMode: StateFlow<AudioMode> = _audioMode.asStateFlow()

    private val _isMicMuted = MutableStateFlow(true)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isPttPressed = MutableStateFlow(false)
    val isPttPressed: StateFlow<Boolean> = _isPttPressed.asStateFlow()

    private val _bluetoothScoState = MutableStateFlow(BluetoothScoState.DISCONNECTED)
    val bluetoothScoState: StateFlow<BluetoothScoState> = _bluetoothScoState.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(true)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private var isScoReceiverRegistered = false

    /**
     * Start Audio Engine (Init AudioRecord & AudioTrack, start listening for BT Headsets).
     */
    @SuppressLint("MissingPermission")
    fun startAudioEngine() {
        scope.launch {
            audioMutex.withLock {
                startBluetoothScoRouting()
                if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    initAudioTrack()
                }
                if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    initAudioRecord()
                }
                startRecordingLoop()
            }
        }
    }

    /**
     * Stop Audio Engine and cleanup hardware resources asynchronously to avoid UI freeze.
     */
    fun stopAudioEngine() {
        scope.launch {
            audioMutex.withLock {
                stopRecordingLoop()
                releaseAudioRecord()
                releaseAudioTrack()
                stopBluetoothScoRouting()
            }
        }
    }

    /**
     * Set active voice chat mode (Push-To-Talk vs Open Mic).
     */
    fun setAudioMode(mode: AudioMode) {
        _audioMode.value = mode
        if (mode == AudioMode.PUSH_TO_TALK) {
            _isMicMuted.value = !_isPttPressed.value
        } else {
            _isPttPressed.value = false
        }
    }

    /**
     * Toggle Mute state for Open Mic mode.
     */
    fun toggleMute() {
        if (_audioMode.value == AudioMode.OPEN_MIC) {
            _isMicMuted.value = !_isMicMuted.value
        }
    }

    /**
     * Set Push-To-Talk button state (Pressed / Released).
     */
    fun setPttState(pressed: Boolean) {
        _isPttPressed.value = pressed
        if (_audioMode.value == AudioMode.PUSH_TO_TALK) {
            _isMicMuted.value = !pressed
        }
    }

    /**
     * Initialize AudioTrack for playback.
     */
    private fun initAudioTrack() {
        releaseAudioTrack()
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
            val bufferSize = Math.max(minBufferSize * 2, CHUNK_SIZE * 12)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                audioTrack = track
                Log.d(TAG, "AudioTrack initialized successfully with buffer size: $bufferSize")
            } else {
                Log.e(TAG, "AudioTrack state is not initialized")
                track.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    /**
     * Initialize AudioRecord for microphone audio capture with noise suppression.
     */
    @SuppressLint("MissingPermission")
    private fun initAudioRecord() {
        releaseAudioRecord()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, skipping AudioRecord initialization")
            return
        }
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
            val bufferSize = Math.max(minBufferSize * 2, CHUNK_SIZE * 8)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT,
                bufferSize
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = record
                attachAudioEffects(record.audioSessionId)
                Log.d(TAG, "AudioRecord initialized successfully")
            } else {
                Log.e(TAG, "AudioRecord failed to initialize (state UNINITIALIZED)")
                record.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to attach hardware audio effects", e)
        }
    }

    /**
     * Coroutine recording loop reading microphone PCM chunks and emitting via Flow.
     */
    private suspend fun startRecordingLoop() {
        recordingJob?.cancelAndJoin()
        recordingJob = scope.launch {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val record = audioRecord ?: return@launch
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized, aborting recording loop")
                return@launch
            }
            try {
                record.startRecording()
                val buffer = ByteArray(CHUNK_SIZE)

                while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readBytes = record.read(buffer, 0, CHUNK_SIZE)
                    if (readBytes > 0) {
                        // Check if mic is unmuted (either Open Mic unmuted OR PTT button held)
                        val shouldStream = !_isMicMuted.value
                        if (shouldStream) {
                            val audioChunk = buffer.copyOf(readBytes)
                            _outgoingAudioFlow.emit(audioChunk)
                        }
                    } else if (readBytes < 0) {
                        Log.w(TAG, "AudioRecord.read returned error code: $readBytes")
                        delay(50L)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording loop error", e)
            } finally {
                try {
                    if (record.state == AudioRecord.STATE_INITIALIZED &&
                        record.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    ) {
                        record.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord in finally", e)
                }
            }
        }
    }

    /**
     * Feed incoming raw PCM audio payload from peer device into AudioTrack speaker output.
     */
    fun playIncomingAudio(audioChunk: ByteArray) {
        try {
            val track = audioTrack
            if (track != null &&
                track.state == AudioTrack.STATE_INITIALIZED &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING
            ) {
                track.write(audioChunk, 0, audioChunk.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing incoming audio chunk", e)
        }
    }

    /**
     * Toggles audio output between Loudspeaker and Bluetooth Headset.
     */
    fun toggleAudioOutput() {
        setAudioOutputSpeaker(!_isSpeakerphoneOn.value)
    }

    @Suppress("DEPRECATION")
    fun setAudioOutputSpeaker(useSpeaker: Boolean) {
        scope.launch {
            audioMutex.withLock {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (useSpeaker) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val devices = audioManager.availableCommunicationDevices
                        val speakerDevice = devices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        if (speakerDevice != null) {
                            audioManager.setCommunicationDevice(speakerDevice)
                        }
                    } else {
                        if (audioManager.isBluetoothScoOn) {
                            audioManager.stopBluetoothSco()
                            audioManager.isBluetoothScoOn = false
                        }
                    }
                    audioManager.isSpeakerphoneOn = true
                    _isSpeakerphoneOn.value = true
                    Log.d(TAG, "Audio output explicitly set to LOUDSPEAKER")
                } else {
                    startBluetoothScoRouting()
                }
            }
        }
    }

    /**
     * Enables Bluetooth SCO / BLE Audio headset microphone and speaker routing.
     * Fallback to built-in loudspeaker when Bluetooth headset is not connected.
     */
    @Suppress("DEPRECATION")
    fun startBluetoothScoRouting() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            // Check for Bluetooth headset first
            val bluetoothDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (bluetoothDevice != null) {
                val success = audioManager.setCommunicationDevice(bluetoothDevice)
                if (success) {
                    audioManager.isSpeakerphoneOn = false
                    _isSpeakerphoneOn.value = false
                    _bluetoothScoState.value = BluetoothScoState.CONNECTED
                    Log.d(TAG, "Modern Bluetooth SCO communication device set: ${bluetoothDevice.productName}")
                    return
                } else {
                    Log.w(TAG, "Failed to set Bluetooth communication device")
                }
            }

            // Check for Wired or USB Headset
            val wiredHeadset = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wiredHeadset != null) {
                val success = audioManager.setCommunicationDevice(wiredHeadset)
                if (success) {
                    audioManager.isSpeakerphoneOn = false
                    _isSpeakerphoneOn.value = false
                    _bluetoothScoState.value = BluetoothScoState.DISCONNECTED
                    Log.d(TAG, "Wired/USB Headset communication device set: ${wiredHeadset.productName}")
                    return
                }
            }
            
            // Fallback to Built-in Loudspeaker when no headset is connected
            val speakerDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speakerDevice != null) {
                audioManager.setCommunicationDevice(speakerDevice)
                Log.d(TAG, "Modern Built-in Speaker set as communication device")
            }
            audioManager.isSpeakerphoneOn = true
            _isSpeakerphoneOn.value = true
            _bluetoothScoState.value = BluetoothScoState.DISCONNECTED
        } else {
            // Legacy Android 8 - 11 SCO routing
            if (!isScoReceiverRegistered) {
                val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                ContextCompat.registerReceiver(context, scoStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
                isScoReceiverRegistered = true
            }
            _bluetoothScoState.value = BluetoothScoState.CONNECTING
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            audioManager.isSpeakerphoneOn = true
            _isSpeakerphoneOn.value = true
        }
    }

    @Suppress("DEPRECATION")
    fun stopBluetoothScoRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            if (isScoReceiverRegistered) {
                try {
                    context.unregisterReceiver(scoStateReceiver)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering SCO state receiver", e)
                }
                isScoReceiverRegistered = false
            }
        }
        audioManager.isSpeakerphoneOn = false
        _isSpeakerphoneOn.value = false
        audioManager.mode = AudioManager.MODE_NORMAL
        _bluetoothScoState.value = BluetoothScoState.DISCONNECTED
    }

    private val scoStateReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) ?: return
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                    audioManager.isSpeakerphoneOn = false
                    _isSpeakerphoneOn.value = false
                    _bluetoothScoState.value = BluetoothScoState.CONNECTED
                    Log.d(TAG, "Legacy Bluetooth SCO Connected")
                }
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    audioManager.isSpeakerphoneOn = true
                    _isSpeakerphoneOn.value = true
                    _bluetoothScoState.value = BluetoothScoState.DISCONNECTED
                    Log.d(TAG, "Legacy Bluetooth SCO Disconnected, falling back to loudspeaker")
                }
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                    _bluetoothScoState.value = BluetoothScoState.CONNECTING
                    Log.d(TAG, "Legacy Bluetooth SCO Connecting...")
                }
            }
        }
    }

    private suspend fun stopRecordingLoop() {
        recordingJob?.cancelAndJoin()
        recordingJob = null
    }

    private fun releaseAudioRecord() {
        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects", e)
        }
        try {
            audioRecord?.let { record ->
                if (record.state == AudioRecord.STATE_INITIALIZED && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }

    fun cleanup() {
        scope.launch {
            audioMutex.withLock {
                stopRecordingLoop()
                releaseAudioRecord()
                releaseAudioTrack()
                stopBluetoothScoRouting()
            }
            scope.cancel()
        }
    }
}
