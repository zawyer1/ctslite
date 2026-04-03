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

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zawyer1.ctslite.data.SearchEngine
import com.zawyer1.ctslite.data.SearchMode
import com.zawyer1.ctslite.utils.ImageSearchUploader
import com.zawyer1.ctslite.utils.ImageUtils
import com.zawyer1.ctslite.ui.components.searchWithGoogleLens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition

/**
 * SearchResultsSheet renders the bottom sheet content of the CircleToSearch overlay.
 *
 * It owns:
 * - The tab row for switching between search engines
 * - The upload and URL generation logic (LaunchedEffects)
 * - The loading indicator
 * - The SearchEngineWebView instances (one per engine)
 *
 * It communicates upward via callbacks:
 * - [onExpandSheet] — called when a new bitmap is selected and results are ready,
 *   asking the parent to expand the bottom sheet
 * - [onClose] — called when Google Lens mode succeeds, asking the parent to close
 * - [onSearchUrlChanged] — notifies the parent of the current search URL so the
 *   header menu's "Copy URL" and "Open in Browser" actions stay accurate
 *
 * The [webViewCache] map is passed in from the parent so that the BackHandler and
 * header menu (Refresh, Copy URL, Open in Browser) can interact with WebViews
 * without needing to know about this composable's internals.
 *
 * @param selectedBitmap         The cropped bitmap to search for. Triggers upload + URL generation.
 * @param searchEngines          The ordered list of engines to display as tabs.
 * @param selectedEngine         The currently active tab.
 * @param onSelectedEngineChange Called when the user taps a different tab.
 * @param desktopModeEngines     The set of engines currently in desktop mode.
 * @param isDarkMode             Whether dark mode is active.
 * @param searchMode             The currently selected search mode.
 * @param webViewCache           Shared map for WebView instances.
 * @param onExpandSheet          Called when the sheet should expand (results ready).
 * @param onClose                Called when the overlay should close (Lens mode success).
 * @param onSearchUrlChanged     Called with the current search URL whenever it changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsSheet(
    selectedBitmap: Bitmap?,
    searchEngines: List<SearchEngine>,
    selectedEngine: SearchEngine,
    onSelectedEngineChange: (SearchEngine) -> Unit,
    desktopModeEngines: Set<SearchEngine>,
    isDarkMode: Boolean,
    searchMode: SearchMode,
    webViewCache: MutableMap<SearchEngine, WebView>,
    onExpandSheet: () -> Unit,
    onClose: () -> Unit,
    onSearchUrlChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hostedImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var uploadFailed by remember { mutableStateOf(false) }
    val preloadedUrls = remember { mutableMapOf<SearchEngine, String>() }
    val initializedEngines = remember { mutableStateListOf<SearchEngine>() }

    // Reset all state when a new bitmap arrives
    LaunchedEffect(selectedBitmap) {
        hostedImageUrl = null
        uploadFailed = false
        onSearchUrlChanged(null)
        preloadedUrls.clear()
        initializedEngines.clear()
        webViewCache.values.forEach { it.destroy() }
        webViewCache.clear()
    }

    // Upload and URL generation — runs when bitmap or hosted URL changes
    LaunchedEffect(selectedBitmap, hostedImageUrl) {
        if (selectedBitmap == null) return@LaunchedEffect
        isLoading = true

        // Google Lens mode — hand off and close
        if (searchMode == SearchMode.GoogleLens) {
            val path = ImageUtils.saveBitmap(context, selectedBitmap)
            val uri = android.net.Uri.fromFile(java.io.File(path))
            val success = searchWithGoogleLens(uri, context)
            if (success) { onClose(); return@LaunchedEffect }
            android.util.Log.e("CTS Lite", "Google Lens failed, falling back to Multi-Search")
        }

        // Expand the sheet now that we have something to show
        onExpandSheet()

        // Upload the image if not already done
        if (hostedImageUrl == null) {
            val url = ImageSearchUploader.uploadToImageHost(selectedBitmap)
            if (url != null) hostedImageUrl = url
            else {
                isLoading = false
                uploadFailed = true
                return@LaunchedEffect
            }
        }

        // Generate search URLs for all engines
        searchEngines.forEach { engine ->
            if (!preloadedUrls.containsKey(engine)) {
                val url = when (engine) {
                    SearchEngine.Google -> ImageSearchUploader.getGoogleLensUrl(hostedImageUrl!!)
                    SearchEngine.Bing -> ImageSearchUploader.getBingUrl(hostedImageUrl!!)
                    SearchEngine.Yandex -> ImageSearchUploader.getYandexUrl(hostedImageUrl!!)
                    SearchEngine.TinEye -> ImageSearchUploader.getTinEyeUrl(hostedImageUrl!!)
                }
                preloadedUrls[engine] = url
            }
        }

        // Notify parent of the initial URL for the selected engine
        onSearchUrlChanged(preloadedUrls[selectedEngine])

        // Smart loading: show selected engine first, then others sequentially
        if (!initializedEngines.contains(selectedEngine)) {
            initializedEngines.add(selectedEngine)
        }
        isLoading = false
        scope.launch {
            searchEngines.forEach { engine ->
                if (engine != selectedEngine) {
                    delay(300)
                    if (!initializedEngines.contains(engine)) {
                        initializedEngines.add(engine)
                    }
                }
            }
        }
    }

    // Keep parent's search URL in sync when the selected tab changes
    LaunchedEffect(selectedEngine, preloadedUrls.keys.toList()) {
        onSearchUrlChanged(preloadedUrls[selectedEngine])
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Tab row
        ScrollableTabRow(
            selectedTabIndex = searchEngines.indexOf(selectedEngine),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
            indicator = {}
        ) {
            searchEngines.forEach { engine ->
                val selected = selectedEngine == engine
                val transition = updateTransition(targetState = selected, label = "TabSelect")
                val scale by transition.animateFloat(label = "Scale") { if (it) 1.05f else 1f }
                val alpha by transition.animateFloat(label = "Alpha") { if (it) 1f else 0.7f }

                Tab(
                    selected = selected,
                    onClick = { onSelectedEngineChange(engine) },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                    text = {
                        Text(
                            engine.name,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            modifier = Modifier
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Upload failure message
            if (uploadFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Image upload failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Both Litterbox and Catbox are unreachable. Check your connection, or draw a new selection to try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Loading indicator
            if (!uploadFailed && (isLoading || (preloadedUrls.containsKey(selectedEngine) && !webViewCache.containsKey(selectedEngine)))) {
                var showLoader by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(100)
                    showLoader = true
                }
                if (showLoader || isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicatorSample()
                    }
                }
            }

            // One SearchEngineWebView per initialized engine, layered by zIndex/alpha
            searchEngines.forEach { engine ->
                if (initializedEngines.contains(engine) && preloadedUrls.containsKey(engine)) {
                    androidx.compose.runtime.key(engine) {
                        SearchEngineWebView(
                            engine = engine,
                            url = preloadedUrls[engine]!!,
                            isSelected = engine == selectedEngine,
                            isDesktop = desktopModeEngines.contains(engine),
                            isDarkMode = isDarkMode,
                            webViewCache = webViewCache,
                        )
                    }
                }
            }
        }
    }
}
