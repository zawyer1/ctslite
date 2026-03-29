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

import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.zawyer1.ctslite.data.SearchEngine

// User agent string constants — shared with CircleToSearchScreen via internal visibility.
internal const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
internal const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

/**
 * SearchEngineWebView renders a single WebView for one search engine, wrapped in a
 * SwipeRefreshLayout to support pull-to-refresh.
 *
 * Dark mode is applied via CSS injection in onPageFinished. The native
 * WebSettingsCompat API was considered but all target search engines (Google,
 * Bing, Yandex, TinEye) declare their own color-scheme, causing Android to defer
 * to the site's own theming and ignore the native API entirely.
 *
 * The injected CSS improves on the original approach by explicitly re-inverting
 * img, video, iframe, canvas, svg, and picture elements so they render with
 * correct colours rather than being double-inverted. An idempotency guard
 * prevents the style from being injected more than once per page load.
 *
 * @param engine       The search engine this WebView represents.
 * @param url          The search results URL to load.
 * @param isSelected   Whether this engine is the currently active tab.
 * @param isDesktop    Whether this engine should use a desktop user agent.
 * @param isDarkMode   Whether dark mode should be applied to this WebView.
 * @param webViewCache The shared map where this composable registers its WebView,
 *                     allowing the parent to call reload(), goBack(), and getUrl().
 */
@Composable
fun SearchEngineWebView(
    engine: SearchEngine,
    url: String,
    isSelected: Boolean,
    isDesktop: Boolean,
    isDarkMode: Boolean,
    webViewCache: MutableMap<SearchEngine, WebView>,
) {
    // When desktop mode or dark mode changes, update the existing WebView in place.
    LaunchedEffect(isDesktop, isDarkMode) {
        val wv = webViewCache[engine] ?: return@LaunchedEffect

        val newUserAgent = if (isDesktop) DESKTOP_UA else MOBILE_UA
        if (wv.settings.userAgentString != newUserAgent) {
            wv.settings.userAgentString = newUserAgent
        }

        // Reload so onPageFinished fires and re-applies (or removes) the dark mode CSS
        wv.reload()
    }

    // Destroy this WebView when the composable leaves composition.
    DisposableEffect(engine) {
        onDispose {
            webViewCache[engine]?.destroy()
            webViewCache.remove(engine)
        }
    }

    AndroidView(
        factory = { ctx ->
            val swipeRefresh = SwipeRefreshLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val webView = if (webViewCache.containsKey(engine)) {
                val existing = webViewCache[engine]!!
                (existing.parent as? ViewGroup)?.removeView(existing)
                existing
            } else {
                createWebView(ctx, engine, isDesktop, isDarkMode).also {
                    webViewCache[engine] = it
                    it.loadUrl(url)
                }
            }

            swipeRefresh.addView(webView)
            swipeRefresh.setOnRefreshListener {
                webView.reload()
                swipeRefresh.isRefreshing = false
            }
            swipeRefresh
        },
        update = { swipeRefresh ->
            val webView = (0 until swipeRefresh.childCount)
                .map { swipeRefresh.getChildAt(it) }
                .filterIsInstance<WebView>()
                .firstOrNull()
            if (webView != null && webView.url != url && url != webView.originalUrl) {
                webView.loadUrl(url)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isSelected) 1f else 0f)
            .graphicsLayer { alpha = if (isSelected) 1f else 0f }
    )
}

/**
 * Creates and configures a new WebView for the given engine.
 * Private to this file — external code interacts only through [SearchEngineWebView].
 */
private fun createWebView(
    ctx: android.content.Context,
    engine: SearchEngine,
    isDesktop: Boolean,
    isDarkMode: Boolean,
): WebView {
    return WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = if (isDesktop) DESKTOP_UA else MOBILE_UA
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        // Captures isDarkMode at creation time.
        // Changes to isDarkMode trigger a reload via LaunchedEffect,
        // which re-fires onPageFinished with the updated value.
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (isDarkMode) view?.loadUrl(buildDarkModeCss())
            }
        }

        isNestedScrollingEnabled = true
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN ->
                    v.parent.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }
}

/**
 * Builds the dark mode CSS injection string.
 *
 * The html element is inverted to darken the page. img, video, iframe, canvas,
 * svg, and picture elements are then re-inverted so they render with correct
 * colours rather than appearing double-inverted.
 *
 * An idempotency guard (checking for the cts-dark-mode style id) prevents the
 * style from being injected more than once if onPageFinished fires multiple
 * times for a single page load, which WebView occasionally does.
 */
internal fun buildDarkModeCss(): String = """
    javascript:(function() {
        if (document.getElementById('cts-dark-mode')) return;
        var style = document.createElement('style');
        style.id = 'cts-dark-mode';
        style.innerHTML = `
            html {
                filter: invert(1) hue-rotate(180deg) !important;
                background: #000 !important;
            }
            img, video, iframe, canvas, svg, picture,
            [style*="background-image"] {
                filter: invert(1) hue-rotate(180deg) !important;
            }
            body {
                background: #000 !important;
            }
        `;
        document.head.appendChild(style);
    })()
""".trimIndent()
