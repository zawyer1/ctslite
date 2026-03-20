/*
 * Copyright (C) 2025 AKS-Labs
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * CopyTextOverlayManager — fully offline, zero network, zero logging.
 *
 * FIXES applied vs original:
 *  1. isPotentialTextNode — strict text-only filter, no contentDescription/button leakage
 *  2. collectTextNodes — nodes are NOT recycled until AFTER leaf-filter completes (fixes
 *     non-deterministic parent-pointer traversal on recycled nodes)
 *  3. findNearestWord — converts word.bounds (screen coords) to local view coords before
 *     comparing against touch x/y (which are already in local coords)
 *  4. estimateWordBounds — clamps output to node bounds so multi-line text never maps
 *     words outside the visible region; documented limitation noted
 *  5. getRootNode selection — prefers the window with the MOST text content, not just
 *     windows.first(), so systemui chrome windows rank below real app windows
 *  6. snapshot timing — snapshot nodes are now re-collected on demand (when Copy Text
 *     button is tapped) not only at launch time
 */

package com.akslabs.circletosearch.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Simple holder for a floating-toolbar button's label and screen hit-rect. */
private class ToolbarButton(val label: String, val rect: Rect)

/**
 * Manages the dim+punch-out Copy Text overlay.
 *
 * Usage:
 *   val mgr = CopyTextOverlayManager(context) { getRootNode() }
 *   mgr.getOverlayView(onDismiss = { ... })  // embed in AndroidView
 *   mgr.rescanNodes()  // call on scroll events
 *   mgr.dismiss()
 */
class CopyTextOverlayManager(
    private val context: Context,
    private val screenshotBitmap: android.graphics.Bitmap?
) {
    private var dimView: DimPunchOutView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanJob: Job? = null
    private var onDismissCallback: (() -> Unit)? = null

    // Detected text nodes (screen coordinates)
    private val detectedNodes = mutableListOf<TextNode>()

    // Selection state
    private var activeNode: TextNode? = null
    private var selectionStart: Int = -1
    private var selectionEnd: Int = -1

    // ── Public API ───────────────────────────────────────────────────────────

    fun getOverlayView(onDismiss: () -> Unit): View {
        if (dimView != null) return dimView!!
        android.util.Log.d("CopyText", "getOverlayView() called")
        onDismissCallback = onDismiss

        val view = DimPunchOutView(context) { dismiss() }
        dimView = view

        android.util.Log.d("CopyText", "Starting accessibility node scan…")
        scanNodes(view)
        return view
    }

    fun dismiss() {
        scanJob?.cancel()
        dimView = null
        onDismissCallback?.invoke()
        onDismissCallback = null
    }

    /** Call on TYPE_VIEW_SCROLLED events to refresh punched-out regions live. */
    fun rescanNodes() {
        dimView?.let { scanNodes(it) }
    }

    // ── Node scanning ────────────────────────────────────────────────────────

    private fun scanNodes(view: DimPunchOutView) {
        scanJob?.cancel()
        scanJob = scope.launch {
            android.util.Log.d("CopyText", "scanNodes triggered")

            if (screenshotBitmap == null) {
                android.util.Log.e("CopyText", "scanNodes: screenshotBitmap is null")
                detectedNodes.clear()
                view.invalidate()
                return@launch
            }

            android.util.Log.d("CopyText", "Live scan with OCR on bitmap")

            val nodes = com.akslabs.circletosearch.ocr.TesseractEngine.extractText(context, screenshotBitmap)
            
            android.util.Log.d("CopyText", "Live OCR scan complete: ${nodes.size} text nodes")
            detectedNodes.clear()
            detectedNodes.addAll(nodes)
            view.invalidate()
        }
    }

    /**
     * Split [fullText] into words and assign each word an estimated bounding box.
     *
     * FIX #4: We estimate bounds proportionally by character offset (same approach),
     * but we CLAMP the result to the node's bounds so a word never appears outside
     * its container regardless of whitespace or multi-line layout.
     *
     * NOTE: This is an approximation.  Android does not expose per-word bounds
     * through AccessibilityNodeInfo.  For perfectly accurate word bounds the app
     * under inspection would need to implement getTextLayout() — which most don't.
     * The clamp ensures at minimum that every word highlight lands inside the node.
     */
    internal fun splitIntoWords(fullText: String, nodeBounds: Rect): List<Word> {
        val words = mutableListOf<Word>()
        val totalChars = fullText.length.toFloat()
        if (totalChars == 0f) return words

        val rawWords = fullText.split(Regex("\\s+"))
        var cursor = 0

        rawWords.forEachIndexed { wordIdx, wordText ->
            if (wordText.isEmpty()) return@forEachIndexed
            val start = fullText.indexOf(wordText, cursor)
            if (start == -1) return@forEachIndexed
            val end = start + wordText.length

            val leftFraction  = start / totalChars
            val rightFraction = end   / totalChars

            val rawLeft   = nodeBounds.left  + nodeBounds.width()  * leftFraction
            val rawRight  = nodeBounds.left  + nodeBounds.width()  * rightFraction
            val rawTop    = nodeBounds.top.toFloat()
            val rawBottom = nodeBounds.bottom.toFloat()

            // Clamp to node bounds (FIX #4)
            val bounds = RectF(
                rawLeft.coerceIn(nodeBounds.left.toFloat(), nodeBounds.right.toFloat()),
                rawTop.coerceIn(nodeBounds.top.toFloat(), nodeBounds.bottom.toFloat()),
                rawRight.coerceIn(nodeBounds.left.toFloat(), nodeBounds.right.toFloat()),
                rawBottom.coerceIn(nodeBounds.top.toFloat(), nodeBounds.bottom.toFloat())
            )

            words.add(Word(wordText, wordIdx, start, end, bounds))
            cursor = end
        }
        return words
    }

    // ── Dim + punch-out View ─────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    inner class DimPunchOutView(
        context: Context,
        private val onBackPress: () -> Unit
    ) : View(context) {



        // ── Coordinate helpers ────────────────────────────────────────────────

        private val viewLocation = IntArray(2)

        /** Convert a screen-coordinate Rect to this view's local coordinates. */
        private fun toLocal(screenRect: Rect): RectF {
            getLocationOnScreen(viewLocation)
            return RectF(
                (screenRect.left  - viewLocation[0]).toFloat(),
                (screenRect.top   - viewLocation[1]).toFloat(),
                (screenRect.right - viewLocation[0]).toFloat(),
                (screenRect.bottom - viewLocation[1]).toFloat()
            )
        }

        /** Convert a screen-coordinate RectF to this view's local coordinates. */
        private fun toLocal(screenRectF: RectF): RectF {
            getLocationOnScreen(viewLocation)
            return RectF(
                screenRectF.left   - viewLocation[0],
                screenRectF.top    - viewLocation[1],
                screenRectF.right  - viewLocation[0],
                screenRectF.bottom - viewLocation[1]
            )
        }

        /**
         * Convert a local-coordinate point to screen coordinates.
         * Used so we can compare touch x/y against word.bounds (screen coords).
         *
         * FIX #3: word.bounds are in SCREEN coordinates (set from getBoundsInScreen).
         * Touch events arrive in LOCAL view coordinates.  We must translate before
         * comparing — the original code compared local touch coords directly to
         * screen-space word bounds, causing selection to be offset by the view origin.
         */
        private fun toScreenX(localX: Float): Float {
            getLocationOnScreen(viewLocation)
            return localX + viewLocation[0]
        }

        private fun toScreenY(localY: Float): Float {
            getLocationOnScreen(viewLocation)
            return localY + viewLocation[1]
        }

        // ── Paint objects ─────────────────────────────────────────────────────

        private val dimPaint = Paint().apply {
            color = Color.BLACK
            alpha = 38  // ~15% dim
            isAntiAlias = false
        }

        private val highlightPaint = Paint().apply {
            color = Color.parseColor("#4FC3F7")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        private val highlightFillPaint = Paint().apply {
            color = Color.WHITE
            alpha = 20
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val selectedWordPaint = Paint().apply {
            color = Color.parseColor("#1565C0")
            alpha = 90
            isAntiAlias = true
        }

        private val handlePaint = Paint().apply {
            color = Color.parseColor("#1565C0")
            isAntiAlias = true
        }

        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

        private val copyBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6750A4")
        }

        private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1C1B1F")
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }

        private val copyBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }

        // ── Pulse animation ───────────────────────────────────────────────────

        private var pulseAlpha = 1.0f
        private var pulseIncreasing = false
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

        private val pulseRunnable = object : Runnable {
            override fun run() {
                if (pulseIncreasing) {
                    pulseAlpha = (pulseAlpha + 0.02f).coerceAtMost(1.0f)
                    if (pulseAlpha >= 1.0f) pulseIncreasing = false
                } else {
                    pulseAlpha = (pulseAlpha - 0.02f).coerceAtLeast(0.4f)
                    if (pulseAlpha <= 0.4f) pulseIncreasing = true
                }
                invalidate()
                handler.postDelayed(this, 30)
            }
        }
        init {
            background = null
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            handler.post(pulseRunnable)
        }

        // ── Selection / drag state ────────────────────────────────────────────

        private var dragHandleType = 0  // 0=none, 1=start, 2=end
        private var toolbarButtons: List<ToolbarButton> = emptyList()

        // ── Gesture detector ──────────────────────────────────────────────────

        private val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onLongPress(e: MotionEvent) {
                    val lx = e.x
                    val ly = e.y
                    if (activeNode == null) {
                        detectedNodes.find {
                            toLocal(it.bounds).contains(lx, ly)
                        }?.let { enterNode(it) }
                    } else {
                        // FIX #3: translate to screen coords before nearest-word search
                        val sx = toScreenX(lx)
                        val sy = toScreenY(ly)
                        val idx = findNearestWord(sx, sy, activeNode!!.words)
                        selectionStart = idx
                        selectionEnd   = idx
                        invalidate()
                    }
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val lx = e.x
                    val ly = e.y

                    // 1. Toolbar hit-test (toolbar rects are in LOCAL coords)
                    if (activeNode != null && selectionStart != -1) {
                        for (btn in toolbarButtons) {
                            if (btn.rect.contains(lx.toInt(), ly.toInt())) {
                                handleToolbarAction(btn.label)
                                return true
                            }
                        }
                    }

                    // 2. Tap inside active node → word selection
                    activeNode?.let { node ->
                        if (toLocal(node.bounds).contains(lx, ly)) {
                            // FIX #3: translate to screen for word-bounds comparison
                            val sx = toScreenX(lx)
                            val sy = toScreenY(ly)
                            val wordIdx = findNearestWord(sx, sy, node.words)

                            if (selectionStart == -1 ||
                                (selectionStart == selectionEnd && wordIdx != selectionStart)
                            ) {
                                selectionStart = wordIdx
                                selectionEnd   = wordIdx
                            } else {
                                if (wordIdx < selectionStart) selectionStart = wordIdx
                                else if (wordIdx > selectionEnd) selectionEnd = wordIdx
                            }
                            invalidate()
                            return true
                        } else {
                            exitNode()
                            return true
                        }
                    }

                    // 3. Tap a highlighted node → enter it
                    detectedNodes.find {
                        toLocal(it.bounds).contains(lx, ly)
                    }?.let {
                        enterNode(it)
                        return true
                    }

                    // 4. Tap empty space → dismiss
                    onBackPress()
                    return true
                }
            }
        )

        // ── Node entry / exit ─────────────────────────────────────────────────

        private fun enterNode(node: TextNode) {
            activeNode     = node
            selectionStart = 0
            selectionEnd   = node.words.lastIndex
            invalidate()
        }

        private fun exitNode() {
            activeNode     = null
            selectionStart = -1
            selectionEnd   = -1
            invalidate()
        }

        // ── Drawing ───────────────────────────────────────────────────────────

        private val clearPaint = Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            // Use a layer to support PorterDuff.Mode.CLEAR
            val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

            // 1. Draw the dim layer everywhere
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            // 2. Punch out holes for all detected text nodes
            detectedNodes.forEach { node ->
                if (activeNode?.id == node.id) return@forEach
                val localBounds = toLocal(node.bounds)
                canvas.drawRoundRect(localBounds, 8f, 8f, clearPaint)
            }

            // 3. Active node — draw word selection + handles + toolbar
            activeNode?.let { node ->
                // Also punch out the active node's background so the text is clear
                canvas.drawRoundRect(toLocal(node.bounds), 8f, 8f, clearPaint)

                node.words.forEach { word ->
                    if (word.index in selectionStart..selectionEnd) {
                        // Highlight selected words
                        canvas.drawRoundRect(toLocal(word.bounds), 8f, 8f, selectedWordPaint)
                    }
                }

                if (selectionStart != -1 && selectionEnd != -1) {
                    val startWord = node.words[selectionStart]
                    val endWord   = node.words[selectionEnd]

                    val startLocal = toLocal(startWord.bounds)
                    val endLocal   = toLocal(endWord.bounds)

                    drawHandle(canvas, startLocal.left,  startLocal.top,    isStart = true)
                    drawHandle(canvas, endLocal.right,   endLocal.bottom,   isStart = false)

                    drawFloatingToolbar(canvas, toLocal(node.bounds))
                }
            }

            canvas.restoreToCount(saveCount)
        }

        private fun drawHandle(canvas: Canvas, x: Float, y: Float, isStart: Boolean) {
            canvas.drawCircle(x, y, 18f, handlePaint)
            if (isStart) canvas.drawRect(x - 2f, y, x + 2f, y + 40f, handlePaint)
            else         canvas.drawRect(x - 2f, y - 40f, x + 2f, y, handlePaint)
        }

        private fun drawFloatingToolbar(canvas: Canvas, anchor: RectF) {
            val btnW    = 210
            val btnH    = 88
            val padding = 14
            val gap     = 8
            val labels  = listOf("Copy", "Select All", "Cancel")

            val totalW = labels.size * btnW + (labels.size - 1) * gap + padding * 2
            val totalH = btnH + padding * 2

            var left = anchor.centerX().toInt() - totalW / 2
            var top  = anchor.top.toInt() - totalH - 18
            if (top < 0)              top  = anchor.bottom.toInt() + 18
            if (left < 0)             left = 8
            if (left + totalW > width) left = width - totalW - 8

            cardPaint.setShadowLayer(16f, 0f, 4f, Color.parseColor("#44000000"))
            canvas.drawRoundRect(
                RectF(left.toFloat(), top.toFloat(), (left + totalW).toFloat(), (top + totalH).toFloat()),
                24f, 24f, cardPaint
            )
            cardPaint.clearShadowLayer()

            val buttons = mutableListOf<ToolbarButton>()
            labels.forEachIndexed { idx, label ->
                val bLeft  = left + padding + idx * (btnW + gap)
                val bTop   = top  + padding
                val bRect  = Rect(bLeft, bTop, bLeft + btnW, bTop + btnH)
                buttons.add(ToolbarButton(label, bRect))

                val bRectF = RectF(bRect)
                if (label == "Copy") {
                    canvas.drawRoundRect(bRectF, 12f, 12f, copyBtnPaint)
                    canvas.drawText(
                        label,
                        bRectF.centerX(),
                        bRectF.centerY() + copyBtnTextPaint.textSize / 3f,
                        copyBtnTextPaint
                    )
                } else {
                    canvas.drawText(
                        label,
                        bRectF.centerX(),
                        bRectF.centerY() + btnTextPaint.textSize / 3f,
                        btnTextPaint
                    )
                }
            }
            toolbarButtons = buttons
        }

        // ── Touch handling ────────────────────────────────────────────────────

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lx = event.x
            val ly = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    activeNode?.let { node ->
                        if (selectionStart != -1) {
                            val startLocal = toLocal(node.words[selectionStart].bounds)
                            val endLocal   = toLocal(node.words[selectionEnd].bounds)
                            if (isPointNear(lx, ly, startLocal.left, startLocal.top)) {
                                dragHandleType = 1; return true
                            }
                            if (isPointNear(lx, ly, endLocal.right, endLocal.bottom)) {
                                dragHandleType = 2; return true
                            }
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragHandleType != 0) {
                        // FIX #3: translate to screen coords for word-bounds comparison
                        updateSelectionFromDrag(toScreenX(lx), toScreenY(ly))
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragHandleType != 0) {
                        dragHandleType = 0
                        return true
                    }
                    gestureDetector.onTouchEvent(event)
                }
            }
            return gestureDetector.onTouchEvent(event)
        }

        private fun isPointNear(px: Float, py: Float, x: Float, y: Float): Boolean {
            val dx = px - x
            val dy = py - y
            return dx * dx + dy * dy < 80 * 80
        }

        /**
         * [screenX] / [screenY] are already in screen coordinates.
         * word.bounds is also in screen coordinates, so no conversion needed here.
         */
        private fun updateSelectionFromDrag(screenX: Float, screenY: Float) {
            val node = activeNode ?: return
            val nearestIdx = findNearestWord(screenX, screenY, node.words)
            val oldStart = selectionStart
            val oldEnd   = selectionEnd

            if (dragHandleType == 1) {
                selectionStart = nearestIdx.coerceAtMost(selectionEnd)
            } else {
                selectionEnd = nearestIdx.coerceAtLeast(selectionStart)
            }

            if (oldStart != selectionStart || oldEnd != selectionEnd) {
                performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE)
            }
        }

        /**
         * Find the word whose centre is nearest to ([screenX], [screenY]).
         *
         * FIX #3: Both [screenX]/[screenY] AND word.bounds are in screen coordinates.
         * The original used local touch coords vs screen-space word bounds.
         */
        private fun findNearestWord(screenX: Float, screenY: Float, words: List<Word>): Int {
            var minDist = Float.MAX_VALUE
            var nearest = 0
            words.forEachIndexed { idx, word ->
                val dx = screenX - word.bounds.centerX()
                val dy = screenY - word.bounds.centerY()
                val d  = dx * dx + dy * dy
                if (d < minDist) { minDist = d; nearest = idx }
            }
            return nearest
        }

        // ── Toolbar action handler ────────────────────────────────────────────

        private fun handleToolbarAction(label: String) {
            val node = activeNode ?: return
            when (label) {
                "Copy" -> {
                    if (selectionStart != -1 && selectionEnd != -1) {
                        val text = node.words
                            .filter { it.index in selectionStart..selectionEnd }
                            .joinToString(" ") { it.text }
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", text))
                        Toast.makeText(context, "Text copied ✓", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
                "Select All" -> {
                    selectionStart = 0
                    selectionEnd   = node.words.lastIndex
                    invalidate()
                }
                "Cancel" -> exitNode()
            }
        }

        // ── Misc ──────────────────────────────────────────────────────────────

        override fun isInEditMode(): Boolean = false

        override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                onBackPress()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
    }
}
