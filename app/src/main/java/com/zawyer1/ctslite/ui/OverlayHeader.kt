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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zawyer1.ctslite.data.SearchEngine

/**
 * OverlayHeader renders the top bar of the CircleToSearch overlay.
 *
 * It is fully stateless — all values it displays and all actions it can
 * trigger are passed in as parameters. This means it only recomposes when
 * one of its inputs actually changes, not whenever any other part of
 * CircleToSearchScreen changes.
 *
 * @param selectedEngine     The currently active search engine, shown as the title.
 * @param isDesktopMode      Whether the selected engine is currently in desktop mode.
 * @param isDarkMode         Whether dark mode is currently enabled.
 * @param showGradientBorder Whether the gradient border is currently visible.
 * @param onClose            Called when the user taps the close button.
 * @param onToggleDesktop    Called when the user toggles desktop/mobile mode.
 * @param onToggleDarkMode   Called when the user toggles dark/light mode.
 * @param onToggleBorder     Called when the user toggles the gradient border.
 * @param onRefresh          Called when the user taps Refresh.
 * @param onCopyUrl          Called when the user taps Copy URL.
 * @param onOpenInBrowser    Called when the user taps Open in Browser.
 * @param onOpenSettings     Called when the user taps Settings.
 */
@Composable
fun OverlayHeader(
    selectedEngine: SearchEngine,
    isDesktopMode: Boolean,
    isDarkMode: Boolean,
    showGradientBorder: Boolean,
    onClose: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleBorder: () -> Unit,
    onRefresh: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // showMenu is the only piece of state owned by this composable.
    // It controls the dropdown and has no effect on the parent screen.
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                .size(40.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Selected engine name as title
        Text(
            text = selectedEngine.name,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        // Menu button and dropdown
        Box(
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (isDesktopMode) "Mobile Mode" else "Desktop Mode") },
                    leadingIcon = {
                        Icon(
                            if (isDesktopMode) Icons.Default.Smartphone else Icons.Default.DesktopWindows,
                            contentDescription = null
                        )
                    },
                    onClick = { onToggleDesktop(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(if (isDarkMode) "Light Mode" else "Dark Mode") },
                    leadingIcon = {
                        Icon(
                            if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null
                        )
                    },
                    onClick = { onToggleDarkMode(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text(if (showGradientBorder) "Hide Border" else "Show Border") },
                    leadingIcon = { Icon(Icons.Default.BorderOuter, contentDescription = null) },
                    onClick = { onToggleBorder(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = { onRefresh(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Copy URL") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = { onCopyUrl(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Open in Browser") },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                    onClick = { onOpenInBrowser(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { onOpenSettings(); showMenu = false }
                )
            }
        }
    }
}
