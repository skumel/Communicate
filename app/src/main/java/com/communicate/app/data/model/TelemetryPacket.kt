package com.communicate.app.data.model

import org.json.JSONObject

/**
 * Data packet sent over P2P connection to synchronize real-time GPS position,
 * speed in mph, heading, and rider metadata.
 */
data class TelemetryPacket(
    val latitude: Double,
    val longitude: Double,
    val speedMph: Float,
    val bearing: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val riderName: String = "Peer Rider"
) {
    fun toByteArray(): ByteArray {
        val json = JSONObject().apply {
            put("lat", latitude)
            put("lng", longitude)
            put("spd", speedMph)
            put("brg", bearing)
            put("ts", timestamp)
            put("name", riderName)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): TelemetryPacket? {
            return try {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                TelemetryPacket(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng"),
                    speedMph = json.getDouble("spd").toFloat(),
                    bearing = json.getDouble("brg").toFloat(),
                    timestamp = json.optLong("ts", System.currentTimeMillis()),
                    riderName = json.optString("name", "Peer Rider")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
