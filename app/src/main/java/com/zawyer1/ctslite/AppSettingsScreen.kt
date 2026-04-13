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

package com.zawyer1.ctslite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zawyer1.ctslite.data.SearchMode
import com.zawyer1.ctslite.ui.EngineOrderItem
import com.zawyer1.ctslite.ui.SettingsSectionHeader
import com.zawyer1.ctslite.ui.SettingsToggleItem
import com.zawyer1.ctslite.utils.UIPreferences

/**
 * AppSettingsScreen is the dedicated settings page accessible from MainActivity.
 *
 * It provides access to all persistent preferences that are relevant outside
 * of an active search session. Settings that are purely session-contextual
 * (Desktop/Mobile mode, Refresh, Copy URL, Open in Browser) remain in the
 * overlay settings sheet only.
 *
 * Navigation is handled via the [onBack] callback, triggered by both the
 * top app bar back arrow and the system back button via BackHandler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uiPreferences = remember { UIPreferences(context) }

    // All preference state — loaded once, persisted on change via LaunchedEffect
    var showFriendlyMessages by remember { mutableStateOf(uiPreferences.isShowFriendlyMessages()) }
    var searchMode by remember { mutableStateOf(uiPreferences.getSearchMode()) }
    var showGradientBorder by remember { mutableStateOf(uiPreferences.isShowGradientBorder()) }
    var isDarkMode by remember { mutableStateOf(uiPreferences.isDarkMode()) }
    val engineOrder = remember {
        mutableStateListOf(*uiPreferences.getOrderedSearchEngines().toTypedArray())
    }

    LaunchedEffect(showFriendlyMessages) { uiPreferences.setShowFriendlyMessages(showFriendlyMessages) }
    LaunchedEffect(searchMode) { uiPreferences.setSearchMode(searchMode) }
    LaunchedEffect(showGradientBorder) { uiPreferences.setShowGradientBorder(showGradientBorder) }
    LaunchedEffect(isDarkMode) { uiPreferences.setDarkMode(isDarkMode) }
    LaunchedEffect(engineOrder.toList()) {
        uiPreferences.setSearchEngineOrder(engineOrder.joinToString(",") { it.name })
    }

    // Handle system back button
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- General ---
            SettingsSectionHeader(title = "General")

            SettingsToggleItem(
                title = "Friendly Messages",
                subtitle = "Show random greeting messages on trigger",
                icon = Icons.Default.ChatBubbleOutline,
                checked = showFriendlyMessages,
                onCheckedChange = { showFriendlyMessages = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Search Preferences ---
            SettingsSectionHeader(title = "Search Preferences")

            Text(
                text = "Default search mode",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Can also be changed from the overlay header during a search session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    onClick = { searchMode = SearchMode.MultiSearch },
                    selected = searchMode == SearchMode.MultiSearch,
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ManageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Multi-Search", style = MaterialTheme.typography.labelLarge)
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    onClick = { searchMode = SearchMode.GoogleLens },
                    selected = searchMode == SearchMode.GoogleLens,
                    icon = {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Google Lens", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Overlay & Search Engine Appearance ---
            SettingsSectionHeader(title = "Overlay & Search Engine Appearance")

            SettingsToggleItem(
                title = "Gradient Border",
                subtitle = "Show rainbow gradient border around the overlay",
                icon = Icons.Default.BorderOuter,
                checked = showGradientBorder,
                onCheckedChange = { showGradientBorder = it }
            )

            SettingsToggleItem(
                title = "Dark Mode",
                subtitle = "Apply dark CSS to search engine WebViews",
                icon = Icons.Default.DarkMode,
                checked = isDarkMode,
                onCheckedChange = { isDarkMode = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Search Engines ---
            SettingsSectionHeader(title = "Search Engines")

            Text(
                text = "Tap arrows to change Multi-Search tab sequence",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column {
                    engineOrder.forEachIndexed { index, engine ->
                        EngineOrderItem(
                            engine = engine,
                            isFirst = index == 0,
                            isLast = index == engineOrder.size - 1,
                            onMoveUp = {
                                if (index > 0) {
                                    val temp = engineOrder[index]
                                    engineOrder[index] = engineOrder[index - 1]
                                    engineOrder[index - 1] = temp
                                }
                            },
                            onMoveDown = {
                                if (index < engineOrder.size - 1) {
                                    val temp = engineOrder[index]
                                    engineOrder[index] = engineOrder[index + 1]
                                    engineOrder[index + 1] = temp
                                }
                            }
                        )
                        if (index < engineOrder.size - 1) {
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
