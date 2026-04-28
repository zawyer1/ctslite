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
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.zawyer1.ctslite.ui.theme.OverlayGradientColors
import com.zawyer1.ctslite.utils.ImageUtils
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * DrawingLayer handles all user interaction for selecting a region of the screenshot.
 *
 * It owns:
 * - The freehand path the user draws (currentPathPoints)
 * - The bounding rectangle derived from that path (selectionRect)
 * - The bracket/lens animation that plays after selection (selectionAnim)
 * - The Canvas with gesture detection and all drawing logic
 *
 * It communicates upward only via [onSelectionComplete], which fires once the
 * bracket animation finishes and delivers the cropped bitmap and its bounding
 * rect to the parent. The parent (CircleToSearchScreen) does not need to know
 * anything about the drawing internals.
 *
 * Isolating this composable means that the high-frequency state updates during
 * drawing (currentPathPoints changes on every touch event, up to 60+ times/sec)
 * are scoped here and do not trigger recomposition in the rest of the screen.
 */
@Composable
fun DrawingLayer(
    screenshot: Bitmap,
    isSquareSelection: Boolean,
    onSelectionComplete: (croppedBitmap: Bitmap, rect: Rect) -> Unit
) {
    val scope = rememberCoroutineScope()

    // All drawing state is local to this composable.
    // Changes to these values do NOT cause CircleToSearchScreen to recompose.
    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    val selectionAnim = remember { Animatable(0f) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSquareSelection) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPathPoints.clear()
                        dragStart = offset
                        dragCurrent = offset
                        selectionRect = null
                        scope.launch { selectionAnim.snapTo(0f) }
                    },
                    onDrag = { change, _ ->
                        if (isSquareSelection) {
                            dragCurrent = change.position
                        } else {
                            currentPathPoints.add(change.position)
                        }
                    },
                    onDragEnd = {
                        val rect = if (isSquareSelection) {
                            val start = dragStart ?: return@detectDragGestures
                            val end = dragCurrent ?: return@detectDragGestures
                            Rect(
                                min(start.x, end.x).toInt(),
                                min(start.y, end.y).toInt(),
                                max(start.x, end.x).toInt(),
                                max(start.y, end.y).toInt()
                            )
                        } else {
                            if (currentPathPoints.isEmpty()) return@detectDragGestures
                            var minX = Float.MAX_VALUE
                            var minY = Float.MAX_VALUE
                            var maxX = Float.MIN_VALUE
                            var maxY = Float.MIN_VALUE
                            currentPathPoints.forEach { p ->
                                minX = min(minX, p.x)
                                minY = min(minY, p.y)
                                maxX = max(maxX, p.x)
                                maxY = max(maxY, p.y)
                            }
                            Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
                        }

                        selectionRect = rect
                        currentPathPoints.clear()
                        dragStart = null
                        dragCurrent = null

                        scope.launch {
                            selectionAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(600)
                            )
                            val cropped = ImageUtils.cropBitmap(screenshot, rect)
                            android.util.Log.d("CircleToSearch", "Selection rect: ${rect.left},${rect.top},${rect.right},${rect.bottom}")
                            android.util.Log.d("CircleToSearch", "Cropped bitmap size: ${cropped.width}x${cropped.height}")
                            onSelectionComplete(cropped, rect)
                        }
                    }
                )
            }
    ) {
        // --- Draw freehand path in real-time (freehand mode only) ---
        if (!isSquareSelection && currentPathPoints.size > 1) {
            val path = Path().apply {
                moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                for (i in 1 until currentPathPoints.size) {
                    lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                }
            }
            // Glow layer
            drawPath(
                path = path,
                brush = Brush.linearGradient(OverlayGradientColors),
                style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                alpha = 0.6f
            )
            // Core layer
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // --- Draw live rectangle preview in real-time (square/rectangle mode only) ---
        if (isSquareSelection && dragStart != null && dragCurrent != null) {
            val start = dragStart!!
            val current = dragCurrent!!
            val left = min(start.x, current.x)
            val top = min(start.y, current.y)
            val width = kotlin.math.abs(current.x - start.x)
            val height = kotlin.math.abs(current.y - start.y)

            // Glow layer
            drawRect(
                brush = Brush.linearGradient(OverlayGradientColors),
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 20f),
                alpha = 0.5f
            )
            // Core layer
            drawRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 3f)
            )
        }

        // --- Draw bracket/lens animation after selection ---
        if (selectionRect != null && selectionAnim.value > 0f) {
            val rect = selectionRect!!
            val progress = selectionAnim.value
            val left = rect.left.toFloat()
            val top = rect.top.toFloat()
            val right = rect.right.toFloat()
            val bottom = rect.bottom.toFloat()

            val width = right - left
            val height = bottom - top
            val cornerRadius = 64f
            val armLength = min(width, height) * 0.2f

            // Top Left bracket
            val tlPath = Path().apply {
                moveTo(left, top + armLength)
                lineTo(left, top + cornerRadius)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(left + armLength, top)
            }
            // Top Right bracket
            val trPath = Path().apply {
                moveTo(right - armLength, top)
                lineTo(right - cornerRadius, top)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(right, top + armLength)
            }
            // Bottom Right bracket
            val brPath = Path().apply {
                moveTo(right, bottom - armLength)
                lineTo(right, bottom - cornerRadius)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(right - armLength, bottom)
            }
            // Bottom Left bracket
            val blPath = Path().apply {
                moveTo(left + armLength, bottom)
                lineTo(left + cornerRadius, bottom)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(left, bottom - armLength)
            }

            val bracketStroke = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            listOf(tlPath, trPath, brPath, blPath).forEach { p ->
                drawPath(p, Color.White, style = bracketStroke, alpha = progress)
                drawPath(
                    p,
                    Brush.linearGradient(OverlayGradientColors),
                    style = Stroke(width = 20f, cap = StrokeCap.Round),
                    alpha = progress * 0.5f
                )
            }

            // Flash effect inside selection
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(32f),
                style = Stroke(width = 4f),
                alpha = (1f - progress) * 0.5f
            )
        }
    }
}
