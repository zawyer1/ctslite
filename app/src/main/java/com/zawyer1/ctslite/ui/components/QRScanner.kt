/*
 *
 *  * Copyright (C) 2025 AKS-Labs (original author)
    *  * Modifications Copyright (C) 2026 Zawyer1
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.zawyer1.ctslite.ui.components

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Represents the decoded result of a QR or barcode scan.
 *
 * Each subclass carries the raw string value and a structured type-specific
 * payload where applicable. The UI uses the type to offer contextually
 * appropriate actions (Open, Copy, etc.).
 */
sealed class QRResult {

    /** A URL — http, https, or any deep link / custom scheme */
    data class Url(val url: String) : QRResult()

    /** Plain text with no recognised structured type */
    data class PlainText(val text: String) : QRResult()

    /** A phone number */
    data class Phone(val number: String) : QRResult()

    /** An email address */
    data class Email(val address: String) : QRResult()

    /** Wi-Fi credentials */
    data class WiFi(val ssid: String, val password: String, val encryptionType: String) : QRResult()

    /**
     * An Android intent:// URI.
     * The raw string is preserved for display and user confirmation before launch.
     */
    data class IntentUri(val raw: String) : QRResult()

    /** Any other structured barcode type — contact, calendar, geo, etc. */
    data class Other(val raw: String, val typeName: String) : QRResult()

    /** Multiple barcodes detected in the same image */
    data class Multiple(val results: List<QRResult>) : QRResult()

    /** No barcode was found in the image */
    object NotFound : QRResult()

    /** ML Kit returned an error */
    data class Error(val message: String) : QRResult()
}

/**
 * Scans a [Bitmap] for QR codes and barcodes using ML Kit's on-device scanner.
 *
 * This is a suspend function — it wraps ML Kit's callback API in a coroutine.
 * It runs on whatever dispatcher the caller provides, but ML Kit itself
 * schedules its work internally so this does not need to be called on IO.
 *
 * Returns a [QRResult] representing the first detected barcode, or
 * [QRResult.NotFound] if no barcode was detected.
 */
suspend fun scanQRCode(bitmap: Bitmap): QRResult = suspendCoroutine { continuation ->
    val image = InputImage.fromBitmap(bitmap, 0)
    val scanner = BarcodeScanning.getClient()

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            if (barcodes.isEmpty()) {
                continuation.resume(QRResult.NotFound)
                return@addOnSuccessListener
            }

            // Parse every detected barcode into a QRResult
            fun parseBarcode(barcode: com.google.mlkit.vision.barcode.common.Barcode): QRResult {
                val raw = barcode.rawValue ?: ""
                return when (barcode.valueType) {
                    Barcode.TYPE_URL -> {
                        val url = barcode.url?.url ?: raw
                        if (url.startsWith("intent://")) QRResult.IntentUri(url)
                        else QRResult.Url(url)
                    }
                    Barcode.TYPE_TEXT -> {
                        when {
                            raw.startsWith("intent://") -> QRResult.IntentUri(raw)
                            raw.startsWith("http://") || raw.startsWith("https://") -> QRResult.Url(raw)
                            else -> QRResult.PlainText(raw)
                        }
                    }
                    Barcode.TYPE_PHONE -> QRResult.Phone(barcode.phone?.number ?: raw)
                    Barcode.TYPE_EMAIL -> QRResult.Email(barcode.email?.address ?: raw)
                    Barcode.TYPE_WIFI -> {
                        val wifi = barcode.wifi
                        val encType = when (wifi?.encryptionType) {
                            Barcode.WiFi.TYPE_WPA -> "WPA"
                            Barcode.WiFi.TYPE_WEP -> "WEP"
                            else -> "Open"
                        }
                        QRResult.WiFi(
                            ssid = wifi?.ssid ?: "",
                            password = wifi?.password ?: "",
                            encryptionType = encType
                        )
                    }
                    else -> {
                        val typeName = when (barcode.valueType) {
                            Barcode.TYPE_CONTACT_INFO -> "Contact"
                            Barcode.TYPE_CALENDAR_EVENT -> "Calendar Event"
                            Barcode.TYPE_GEO -> "Location"
                            Barcode.TYPE_ISBN -> "ISBN"
                            Barcode.TYPE_PRODUCT -> "Product"
                            Barcode.TYPE_DRIVER_LICENSE -> "Driver Licence"
                            else -> "Barcode"
                        }
                        QRResult.Other(raw, typeName)
                    }
                }
            }

            val parsed = barcodes.map { parseBarcode(it) }

            val result = if (parsed.size == 1) parsed.first()
                         else QRResult.Multiple(parsed)

            continuation.resume(result)
        }
        .addOnFailureListener { e ->
            continuation.resume(QRResult.Error(e.message ?: "Unknown error"))
        }
}
