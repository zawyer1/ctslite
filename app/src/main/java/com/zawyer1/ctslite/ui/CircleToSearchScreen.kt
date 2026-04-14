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
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zawyer1.ctslite.ui.components.FriendlyMessageBubble
import com.zawyer1.ctslite.ui.theme.OverlayGradientColors
import com.zawyer1.ctslite.utils.FriendlyMessageManager
import com.zawyer1.ctslite.utils.UIPreferences
import com.zawyer1.ctslite.data.SearchEngine
import com.zawyer1.ctslite.data.SearchMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CircleToSearchScreen is the root composable for the overlay experience.
 *
 * After Steps 1–4 it is now a pure layout coordinator — it owns only the
 * state that multiple child composables need to share, and delegates all
 * rendering and logic to its children:
 *
 *   DrawingLayer        — freehand selection on the screenshot
 *   OverlayHeader       — close button and menu
 *   SearchResultsSheet  — upload, URL generation, tabs, WebViews
 *
 * State owned here:
 *   selectedBitmap      — the cropped result from DrawingLayer, input to SearchResultsSheet
 *   selectedEngine      — shared between OverlayHeader (title) and SearchResultsSheet (tabs)
 *   desktopModeEngines  — toggled via OverlayHeader, consumed by SearchResultsSheet
 *   isDarkMode          — toggled via OverlayHeader, consumed by SearchResultsSheet
 *   showGradientBorder  — toggled via OverlayHeader, consumed by the border layer here
 *   searchUrl           — reported up from SearchResultsSheet, used by OverlayHeader menu
 *   webViews            — shared cache: SearchResultsSheet writes, BackHandler+header reads
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleToSearchScreen(
    screenshot: Bitmap?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiPreferences = remember { UIPreferences(context) }

    // Search engine order
    // Engine order is resolved once and cached until the saved order string changes.
    val searchEngines = remember(uiPreferences.getSearchEngineOrder()) {
        uiPreferences.getOrderedSearchEngines()
    }

    var showSettingsScreen by remember { mutableStateOf(false) }

    // Friendly message
    var friendlyMessage by remember { mutableStateOf("") }
    var isMessageVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (uiPreferences.isShowFriendlyMessages()) {
            friendlyMessage = FriendlyMessageManager(context).getNextMessage()
            delay(500)
            isMessageVisible = true
            delay(4000)
            isMessageVisible = false
        }
    }

    // Shared state — read by multiple children
    var selectedEngine by remember(searchEngines) { mutableStateOf<SearchEngine>(searchEngines.first()) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var searchUrl by remember { mutableStateOf<String?>(null) }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var desktopModeEngines by remember {
        mutableStateOf<Set<SearchEngine>>(uiPreferences.getDesktopModeEngines(searchEngines))
    }
    var isDarkMode by remember { mutableStateOf(uiPreferences.isDarkMode()) }
    var showGradientBorder by remember { mutableStateOf(uiPreferences.isShowGradientBorder()) }
    var searchMode by remember { mutableStateOf(uiPreferences.getSearchMode()) }

    LaunchedEffect(isDarkMode) { uiPreferences.setDarkMode(isDarkMode) }
    LaunchedEffect(showGradientBorder) { uiPreferences.setShowGradientBorder(showGradientBorder) }
    LaunchedEffect(desktopModeEngines) { uiPreferences.setDesktopModeEngines(desktopModeEngines) }
    LaunchedEffect(searchMode) { uiPreferences.setSearchMode(searchMode) }

    // WebView cache — SearchResultsSheet writes, BackHandler + header menu reads
    val webViews = remember { mutableMapOf<SearchEngine, WebView>() }
    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    // Bottom sheet scaffold state
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    BackHandler(enabled = true) {
        val currentWebView = webViews[selectedEngine]
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack()
        } else if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        } else if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
            scope.launch { scaffoldState.bottomSheetState.hide() }
        } else {
            onClose()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * 0.55f).toDp()
        },
        sheetContainerColor = Color.Transparent,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        sheetSwipeEnabled = true,
        sheetContent = {
            // STEP 4: All sheet content extracted to SearchResultsSheet.
            SearchResultsSheet(
                selectedBitmap = selectedBitmap,
                searchEngines = searchEngines,
                selectedEngine = selectedEngine,
                onSelectedEngineChange = { selectedEngine = it },
                desktopModeEngines = desktopModeEngines,
                isDarkMode = isDarkMode,
                searchMode = searchMode,
                webViewCache = webViews,
                onExpandSheet = { scope.launch { scaffoldState.bottomSheetState.expand() } },
                onClose = onClose,
                onSearchUrlChanged = { searchUrl = it },
                onImageUrlChanged = { imageUrl = it },
            )
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Friendly message bubble
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 100.dp)
                    .zIndex(100f),
                contentAlignment = Alignment.TopCenter
            ) {
                FriendlyMessageBubble(message = friendlyMessage, visible = isMessageVisible)
            }

            // Screenshot background
            if (screenshot != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = screenshot.asImageBitmap(),
                        contentDescription = "Screenshot",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = OverlayGradientColors.map { it.copy(alpha = 0.3f) }
                                )
                            )
                    )
                }
            }

            // Gradient border
            if (showGradientBorder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 8.dp,
                            brush = Brush.verticalGradient(colors = OverlayGradientColors),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            // Step 1: Drawing layer
            if (screenshot != null) {
                DrawingLayer(
                    screenshot = screenshot,
                    onSelectionComplete = { croppedBitmap, _ ->
                        selectedBitmap = croppedBitmap
                    }
                )
            }

            // Step 2: Header
            OverlayHeader(
                searchMode = searchMode,
                onModeChange = { searchMode = it },
                hasActiveSearch = searchUrl != null,
                hasImageUrl = imageUrl != null,
                isDesktopMode = desktopModeEngines.contains(selectedEngine),
                isDarkMode = isDarkMode,
                showGradientBorder = showGradientBorder,
                onClose = onClose,
                onToggleDesktop = {
                    val newSet = desktopModeEngines.toMutableSet()
                    if (newSet.contains(selectedEngine)) newSet.remove(selectedEngine)
                    else newSet.add(selectedEngine)
                    desktopModeEngines = newSet
                },
                onToggleDarkMode = { isDarkMode = !isDarkMode },
                onToggleBorder = { showGradientBorder = !showGradientBorder },
                onRefresh = { webViews[selectedEngine]?.reload() },
                onCopyUrl = {
                    if (searchUrl != null) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Search URL", searchUrl)
                        )
                    }
                },
                onCopyImageUrl = {
                    if (imageUrl != null) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Image URL", imageUrl)
                        )
                    }
                },
                onOpenInBrowser = {
                    val currentUrl = webViews[selectedEngine]?.url ?: searchUrl
                    if (currentUrl != null) {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    currentUrl.toUri()
                                )
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("CircleToSearch", "Failed to open browser", e)
                        }
                    }
                },
                onOpenSettings = { showSettingsScreen = true },
            )

            // Search pill (bottom) — tapping expands the sheet
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(colors = OverlayGradientColors),
                        shape = CircleShape
                    )
                    .background(Color(0xFF1F1F1F).copy(alpha = 0.6f), CircleShape)
                    .height(64.dp)
                    .padding(horizontal = 20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            scope.launch { scaffoldState.bottomSheetState.expand() }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side — thumbnail when a selection has been made, empty otherwise
                    if (selectedBitmap != null) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Centre — Full Screen button, always visible
                    OutlinedButton(
                        onClick = {
                            if (screenshot != null) {
                                selectedBitmap = screenshot
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.White.copy(alpha = 0.4f)
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 6.dp
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Full Screen",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Right — app label
                    Text(
                        text = "CTS Lite",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }


            if (showSettingsScreen) {
                SettingsScreen(
                    uiPreferences = uiPreferences,
                    onDismissRequest = { showSettingsScreen = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun ContainedLoadingIndicatorSample() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ContainedLoadingIndicator()
    }
}
