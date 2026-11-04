package com.communicate.app.data.model

enum class ConnectionStatus {
    DISCONNECTED,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED
}

enum class AudioMode {
    PUSH_TO_TALK,
    OPEN_MIC
}

enum class BluetoothScoState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class LocalRiderState(
    val deviceName: String = "Rider Device",
    val speedMph: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val isMicMuted: Boolean = true,
    val isPttPressed: Boolean = false,
    val audioMode: AudioMode = AudioMode.PUSH_TO_TALK,
    val bluetoothScoState: BluetoothScoState = BluetoothScoState.DISCONNECTED,
    val isBluetoothScoActive: Boolean = false,
    val isSpeakerphoneOn: Boolean = true
)

data class PeerRiderState(
    val endpointId: String? = null,
    val name: String = "Peer Rider",
    val isConnected: Boolean = false,
    val speedMph: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val isReceivingAudio: Boolean = false,
    val lastSeenTimestamp: Long = 0L
)
