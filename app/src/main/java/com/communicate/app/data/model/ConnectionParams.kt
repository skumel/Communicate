package com.communicate.app.data.model

import org.json.JSONObject

/**
 * Connection parameters encoded into the QR code for instant P2P pairing.
 */
data class ConnectionParams(
    val deviceName: String,
    val serviceId: String = SERVICE_ID,
    val authCode: String = generateAuthCode()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("name", deviceName)
            put("sid", serviceId)
            put("code", authCode)
        }.toString()
    }

    companion object {
        const val SERVICE_ID = "com.communicate.app.p2p"

        fun fromJson(jsonStr: String): ConnectionParams? {
            return try {
                val json = JSONObject(jsonStr)
                ConnectionParams(
                    deviceName = json.getString("name"),
                    serviceId = json.optString("sid", SERVICE_ID),
                    authCode = json.optString("code", "")
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun generateAuthCode(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..6).map { chars.random() }.joinToString("")
        }
    }
}
