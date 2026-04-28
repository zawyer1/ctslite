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

package com.zawyer1.ctslite.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.zawyer1.ctslite.ui.components.QRResult

/**
 * QRResultSheet renders the bottom sheet content when QR Scan mode is active.
 *
 * Displays the decoded result with contextually appropriate actions:
 * - URL / deep link: Open in Browser + Copy
 * - intent:// URI: Copy raw value + Open with confirmation dialog (security prompt)
 * - Phone: Copy
 * - Email: Copy
 * - Wi-Fi: Copy credentials
 * - Plain text / Other: Copy
 * - Not found: Error message
 * - Scanning: Loading indicator
 *
 * intent:// URIs always require explicit user confirmation before launching
 * to prevent malicious QR codes from silently triggering app actions.
 */
@Composable
fun QRResultSheet(
    result: QRResult?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showIntentConfirmDialog by remember(result) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "QR Scan Result",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        when (result) {
            null -> {
                // Still scanning
                ContainedLoadingIndicatorSample()
            }

            QRResult.NotFound -> {
                QRMessageCard(
                    icon = { Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
                    title = "No QR Code Found",
                    subtitle = "No QR code or barcode was detected in the selected region. Try drawing a tighter selection around the code.",
                    isError = true
                )
            }

            is QRResult.Error -> {
                QRMessageCard(
                    icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
                    title = "Scan Error",
                    subtitle = result.message,
                    isError = true
                )
            }

            is QRResult.Url -> {
                QRTypeLabel("URL")
                QRValueCard(result.url)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, result.url.toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "No app found to open this URL", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open")
                    }
                    OutlinedButton(
                        onClick = { copyToClipboard(context, result.url, "URL") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }

            is QRResult.IntentUri -> {
                QRTypeLabel("App Intent")
                QRValueCard(result.raw)
                Spacer(modifier = Modifier.height(8.dp))

                // Security warning
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "This QR code contains an app intent that will launch an action in another app. Only proceed if you trust the source of this QR code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showIntentConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open")
                    }
                    OutlinedButton(
                        onClick = { copyToClipboard(context, result.raw, "Intent URI") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }

                // Confirmation dialog for intent:// URIs
                if (showIntentConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showIntentConfirmDialog = false },
                        icon = {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = { Text("Open App Intent?") },
                        text = {
                            Column {
                                Text(
                                    "This will launch an action in another app using the following intent:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    result.raw,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Only proceed if you trust the source of this QR code.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showIntentConfirmDialog = false
                                    try {
                                        val intent = Intent.parseUri(
                                            result.raw,
                                            Intent.URI_INTENT_SCHEME
                                        ).apply {
                                            // Restrict to VIEW and SEND only for safety
                                            if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) {
                                                action = Intent.ACTION_VIEW
                                            }
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not launch intent: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Open Anyway") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showIntentConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            is QRResult.Phone -> {
                QRTypeLabel("Phone Number")
                QRValueCard(result.number)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, "tel:${result.number}".toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open dialler", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dial")
                    }
                    OutlinedButton(
                        onClick = { copyToClipboard(context, result.number, "Phone number") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }

            is QRResult.Email -> {
                QRTypeLabel("Email Address")
                QRValueCard(result.address)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_SENDTO, "mailto:${result.address}".toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open email app", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Email")
                    }
                    OutlinedButton(
                        onClick = { copyToClipboard(context, result.address, "Email address") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }

            is QRResult.WiFi -> {
                QRTypeLabel("Wi-Fi Network")
                QRValueCard("SSID: ${result.ssid}\nSecurity: ${result.encryptionType}")
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val credentials = "SSID: ${result.ssid}\nPassword: ${result.password}\nSecurity: ${result.encryptionType}"
                            copyToClipboard(context, credentials, "Wi-Fi credentials")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Wifi, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Credentials")
                    }
                }
                if (result.password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { copyToClipboard(context, result.password, "Wi-Fi password") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Password Only")
                    }
                }
            }

            is QRResult.PlainText -> {
                QRTypeLabel("Text")
                QRValueCard(result.text)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { copyToClipboard(context, result.text, "Text") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                                        putExtra(android.app.SearchManager.QUERY, result.text)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (e: Exception) {
                                // Fallback to browser search
                                val query = java.net.URLEncoder.encode(result.text, "UTF-8")
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$query".toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search")
                    }
                }
            }

            is QRResult.Other -> {
                QRTypeLabel(result.typeName)
                QRValueCard(result.raw)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { copyToClipboard(context, result.raw, result.typeName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }
        }
    }
}

@Composable
private fun QRTypeLabel(type: String) {
    Text(
        text = type.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun QRValueCard(value: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun QRMessageCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isError: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
