package com.communicate.app.network

import android.content.Context
import android.util.Log
import com.communicate.app.data.model.ConnectionParams
import com.communicate.app.data.model.ConnectionStatus
import com.communicate.app.data.model.PeerRiderState
import com.communicate.app.data.model.TelemetryPacket
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
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
import kotlinx.coroutines.launch

class P2PConnectionManager(private val context: Context) {

    private val TAG = "P2PConnectionManager"
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _peerState = MutableStateFlow(PeerRiderState())
    val peerState: StateFlow<PeerRiderState> = _peerState.asStateFlow()

    private val _audioIncomingFlow = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioIncomingFlow: SharedFlow<ByteArray> = _audioIncomingFlow.asSharedFlow()

    private val _telemetryIncomingFlow = MutableSharedFlow<TelemetryPacket>(extraBufferCapacity = 16)
    val telemetryIncomingFlow: SharedFlow<TelemetryPacket> = _telemetryIncomingFlow.asSharedFlow()

    private var activeEndpointId: String? = null
    private var isConnectionResolved: Boolean = false
    private var isPeerAuthenticated: Boolean = false
    private var discoveryJob: Job? = null
    private var authTimeoutJob: Job? = null
    private var peerAudioActiveJob: Job? = null
    private var expectedAuthCode: String? = null
    private var localAuthCode: String? = null
    private var localDeviceName: String = "Rider Device"

    companion object {
        private const val HEADER_AUDIO: Byte = 0x01
        private const val HEADER_TELEMETRY: Byte = 0x02
        private const val HEADER_AUTH: Byte = 0x03
    }

    /**
     * Start advertising to allow peer riders to discover and connect.
     */
    fun startAdvertising(localName: String, authCode: String) {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) return

        // Stop any existing advertising or discovery session before starting a new one with a fresh authCode
        discoveryJob?.cancel()
        discoveryJob = null
        stopDiscovery()
        stopAdvertising()

        activeEndpointId = null
        isConnectionResolved = false
        isPeerAuthenticated = false
        this.localDeviceName = localName
        localAuthCode = authCode
        expectedAuthCode = authCode
        _connectionStatus.value = ConnectionStatus.ADVERTISING
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        val advertisingName = "$localName#$authCode"
        connectionsClient.startAdvertising(
            advertisingName,
            ConnectionParams.SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Started advertising as $advertisingName")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed", e)
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    /**
     * Explicitly cancel advertising if the QR dialog is dismissed before a peer connects.
     */
    fun cancelAdvertising() {
        stopAdvertising()
        if (_connectionStatus.value == ConnectionStatus.ADVERTISING) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    /**
     * Start discovery after scanning peer's QR code.
     */
    fun startDiscoveryAndConnect(params: ConnectionParams, localName: String) {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) return

        discoveryJob?.cancel()
        discoveryJob = null
        stopAdvertising()
        stopDiscovery()

        activeEndpointId = null
        isConnectionResolved = false
        isPeerAuthenticated = false
        this.localDeviceName = localName
        expectedAuthCode = params.authCode
        localAuthCode = params.authCode
        
        _connectionStatus.value = ConnectionStatus.DISCOVERING
        
        discoveryJob = scope.launch {
            delay(30000L) // 30 second timeout
            if (_connectionStatus.value == ConnectionStatus.DISCOVERING) {
                Log.w(TAG, "Discovery timed out after 30 seconds")
                stopDiscovery()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }

        val options = com.google.android.gms.nearby.connection.DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startDiscovery(
            params.serviceId,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d(TAG, "Discovered endpoint: $endpointId (${info.endpointName})")
                    
                    // Verify authCode from QR matches advertising name
                    val endpointName = info.endpointName
                    if (endpointName.contains("#")) {
                        val nameParts = endpointName.split("#")
                        val peerAuthCode = nameParts.last()
                        if (peerAuthCode == expectedAuthCode) {
                            Log.d(TAG, "Authentication matching for QR code endpoint $endpointId")
                            discoveryJob?.cancel()
                            stopDiscovery()
                            requestConnection(endpointId, localName)
                        } else {
                            Log.w(TAG, "Auth code mismatch: expected $expectedAuthCode, got $peerAuthCode")
                        }
                    } else {
                        Log.w(TAG, "Discovered endpoint with no auth code: $endpointName")
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d(TAG, "Endpoint lost: $endpointId")
                }
            },
            options
        ).addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed", e)
            discoveryJob?.cancel()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    /**
     * Explicitly cancel scanning/discovery if interrupted or closed by user.
     */
    fun cancelDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        stopDiscovery()
        if (_connectionStatus.value == ConnectionStatus.DISCOVERING) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    private fun requestConnection(endpointId: String, localName: String) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                Log.e(TAG, "Connection request failed", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: $endpointId (${info.endpointName})")
            if (_connectionStatus.value == ConnectionStatus.CONNECTED ||
                (activeEndpointId != null && activeEndpointId != endpointId)
            ) {
                Log.w(TAG, "Rejecting connection from $endpointId because another session is active ($activeEndpointId)")
                connectionsClient.rejectConnection(endpointId)
                return
            }

            activeEndpointId = endpointId
            isConnectionResolved = false
            isPeerAuthenticated = false
            val cleanName = info.endpointName.substringBeforeLast("#")
            _peerState.value = _peerState.value.copy(
                endpointId = endpointId,
                name = cleanName,
                isConnected = false
            )
            // Auto-accept pairing handshake
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (activeEndpointId != null && endpointId != activeEndpointId) return

            if (result.status.isSuccess) {
                Log.d(TAG, "Connected to $endpointId successfully, authenticating challenge/response...")
                activeEndpointId = endpointId
                isConnectionResolved = true
                stopAdvertising()
                stopDiscovery()

                // Send mutual auth challenge code
                localAuthCode?.let { sendAuthCode(it) }

                if (isPeerAuthenticated) {
                    completeAuthentication()
                } else {
                    _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                    // Start 10-second post-connection authentication timeout
                    authTimeoutJob?.cancel()
                    authTimeoutJob = scope.launch {
                        delay(10000L)
                        if (_connectionStatus.value == ConnectionStatus.AUTHENTICATING) {
                            Log.w(TAG, "Mutual authentication timed out for $endpointId! Disconnecting.")
                            disconnect()
                        }
                    }
                }
            } else {
                Log.e(TAG, "Connection rejected or failed: ${result.status.statusCode}")
                activeEndpointId = null
                isConnectionResolved = false
                isPeerAuthenticated = false
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _peerState.value = PeerRiderState()
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            if (activeEndpointId == endpointId || activeEndpointId == null) {
                activeEndpointId = null
                isConnectionResolved = false
                isPeerAuthenticated = false
                authTimeoutJob?.cancel()
                peerAudioActiveJob?.cancel()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _peerState.value = PeerRiderState()
            }
        }
    }

    private fun completeAuthentication() {
        if (!isConnectionResolved || !isPeerAuthenticated) return
        authTimeoutJob?.cancel()
        authTimeoutJob = null
        _connectionStatus.value = ConnectionStatus.CONNECTED
        _peerState.value = _peerState.value.copy(
            isConnected = true,
            lastSeenTimestamp = System.currentTimeMillis()
        )
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (activeEndpointId != null && endpointId != activeEndpointId) return
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                if (bytes.isEmpty()) return

                when (bytes[0]) {
                    HEADER_AUDIO -> {
                        // Guard: Reject audio packets from unauthenticated peers
                        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
                            Log.w(TAG, "Ignored audio payload from unauthenticated peer $endpointId")
                            return
                        }
                        val audioData = bytes.copyOfRange(1, bytes.size)
                        _audioIncomingFlow.tryEmit(audioData)

                        if (!_peerState.value.isReceivingAudio) {
                            _peerState.value = _peerState.value.copy(isReceivingAudio = true)
                        }
                        peerAudioActiveJob?.cancel()
                        peerAudioActiveJob = scope.launch {
                            delay(350L)
                            _peerState.value = _peerState.value.copy(isReceivingAudio = false)
                        }
                    }
                    HEADER_TELEMETRY -> {
                        // Guard: Reject telemetry packets from unauthenticated peers
                        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
                            Log.w(TAG, "Ignored telemetry payload from unauthenticated peer $endpointId")
                            return
                        }
                        val jsonBytes = bytes.copyOfRange(1, bytes.size)
                        TelemetryPacket.fromByteArray(jsonBytes)?.let { packet ->
                            _peerState.value = _peerState.value.copy(
                                speedMph = packet.speedMph,
                                latitude = packet.latitude,
                                longitude = packet.longitude,
                                bearing = packet.bearing,
                                name = packet.riderName,
                                lastSeenTimestamp = packet.timestamp
                            )
                            scope.launch {
                                _telemetryIncomingFlow.emit(packet)
                            }
                        }
                    }
                    HEADER_AUTH -> {
                        val receivedCode = String(bytes.copyOfRange(1, bytes.size), Charsets.UTF_8)
                        Log.d(TAG, "Received auth code handshake: $receivedCode")
                        if (!expectedAuthCode.isNullOrEmpty() && receivedCode == expectedAuthCode) {
                            Log.d(TAG, "Auth challenge handshake successful")
                            isPeerAuthenticated = true
                            completeAuthentication()
                        } else {
                            Log.w(TAG, "Auth handshake failed: expected $expectedAuthCode, got $receivedCode. Disconnecting.")
                            disconnect()
                        }
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op for small low-latency packet updates
        }
    }

    /**
     * Send PCM Audio chunk over P2P connection.
     */
    fun sendAudioChunk(audioBytes: ByteArray) {
        val endpointId = activeEndpointId ?: return
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) return

        val payloadBytes = ByteArray(1 + audioBytes.size)
        payloadBytes[0] = HEADER_AUDIO
        System.arraycopy(audioBytes, 0, payloadBytes, 1, audioBytes.size)

        connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadBytes))
    }

    /**
     * Send GPS Telemetry packet over P2P connection.
     */
    fun sendTelemetry(packet: TelemetryPacket) {
        val endpointId = activeEndpointId ?: return
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) return

        val dataBytes = packet.toByteArray()
        val payloadBytes = ByteArray(1 + dataBytes.size)
        payloadBytes[0] = HEADER_TELEMETRY
        System.arraycopy(dataBytes, 0, payloadBytes, 1, dataBytes.size)

        connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadBytes))
    }

    private fun sendAuthCode(authCode: String) {
        val endpointId = activeEndpointId ?: return
        val codeBytes = authCode.toByteArray(Charsets.UTF_8)
        val payloadBytes = ByteArray(1 + codeBytes.size)
        payloadBytes[0] = HEADER_AUTH
        System.arraycopy(codeBytes, 0, payloadBytes, 1, codeBytes.size)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadBytes))
    }

    fun stopAdvertising() {
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }

    fun stopDiscovery() {
        try {
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }

    fun disconnect() {
        discoveryJob?.cancel()
        discoveryJob = null
        authTimeoutJob?.cancel()
        authTimeoutJob = null
        peerAudioActiveJob?.cancel()
        peerAudioActiveJob = null
        stopAdvertising()
        stopDiscovery()
        activeEndpointId?.let {
            connectionsClient.disconnectFromEndpoint(it)
        }
        connectionsClient.stopAllEndpoints()
        activeEndpointId = null
        isConnectionResolved = false
        isPeerAuthenticated = false
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _peerState.value = PeerRiderState()
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}
