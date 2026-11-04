package com.communicate.app.network

import android.graphics.Bitmap
import com.communicate.app.data.model.ConnectionParams
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

object QRCodeManager {

    /**
     * Generates a QR Code Bitmap encoding connection parameters.
     */
    fun generateQRCode(params: ConnectionParams, width: Int = 512, height: Int = 512): Bitmap? {
        return try {
            val content = params.toJson()
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
            val encoder = BarcodeEncoder()
            encoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses scanned text content into ConnectionParams.
     */
    fun parseQRCode(rawText: String): ConnectionParams? {
        return ConnectionParams.fromJson(rawText)
    }
}
