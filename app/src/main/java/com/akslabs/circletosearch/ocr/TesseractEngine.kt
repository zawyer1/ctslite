package com.akslabs.circletosearch.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.akslabs.circletosearch.ui.components.TextNode
import com.akslabs.circletosearch.ui.components.Word
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object TesseractEngine {
    private const val TAG = "TesseractEngine"
    private var isPrepared = false

    /**
     * Ensures the tessdata folder and eng.traineddata exist in the app's internal files directory.
     * Tesseract requires this specific directory structure (`tessdata/`).
     */
    fun prepareTessData(context: Context): String {
        val filesDir = context.filesDir.absolutePath
        val tessDir = File(filesDir, "tessdata")
        if (!tessDir.exists()) {
            tessDir.mkdirs()
        }

        val engFile = File(tessDir, "eng.traineddata")
        if (!engFile.exists()) {
            Log.d(TAG, "Copying eng.traineddata from assets...")
            try {
                context.assets.open("tessdata/eng.traineddata").use { input ->
                    FileOutputStream(engFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy eng.traineddata: ${e.message}")
            }
        }
        isPrepared = true
        return filesDir
    }

    suspend fun extractText(context: Context, bitmap: Bitmap): List<TextNode> = withContext(Dispatchers.Default) {
        val result = mutableListOf<TextNode>()
        try {
            val dataPath = prepareTessData(context)
            Log.d(TAG, "Initializing Tesseract with dataPath=$dataPath")

            val prefs = context.getSharedPreferences("OcrSettings", Context.MODE_PRIVATE)
            val lang = prefs.getString("selected_lang", "eng") ?: "eng"

            val tess = TessBaseAPI()
            if (!tess.init(dataPath, lang)) {
                Log.e(TAG, "Failed to initialize Tesseract API with language '$lang'!")
                return@withContext emptyList()
            }

            tess.setImage(bitmap)
            
            // CRITICAL: We MUST call getUTF8Text() (or other recognition trigger) before accessing the iterator.
            // Otherwise, resultIterator will be null or empty.
            val fullAppText = tess.getUTF8Text()
            Log.d(TAG, "Tesseract recognition complete. Full text length: ${fullAppText?.length ?: 0}")

            // We iterate over text lines so we can group words together into a TextNode,
            // which gives the user a cleaner multi-word bounding box for copying blocks.
            val iterator = tess.resultIterator ?: run {
                Log.e(TAG, "ResultIterator is null after recognition!")
                tess.recycle()
                return@withContext emptyList()
            }
            
            iterator.begin()
            
            do {
                val blockText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                if (blockText.isNullOrBlank()) continue
                
                val blockRectParams = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE) 
                                     ?: iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                
                val parentRect = if (blockRectParams is Rect) {
                    blockRectParams
                } else if (blockRectParams is IntArray) {
                    Rect(blockRectParams[0], blockRectParams[1], blockRectParams[2], blockRectParams[3])
                } else {
                    continue
                }

                if (parentRect.isEmpty || parentRect.width() < 5) continue

                val words = mutableListOf<Word>()
                // Standard Tesseract integration for Words within a block is complex via iterator if we don't restart iteration,
                // so we just tokenize the line based on the bounding box proportionally as we used to do,
                // or we treat the whole block as one word segment for now since Tesseract handles lines well.
                // For optimal UX, we'll mimic the splitIntoWords logic.
                
                // Let's do simple proportional splitting
                val segments = blockText.split(Regex("\\s+")).filter { it.isNotBlank() }
                var startOffsetChars = 0
                val totalChars = blockText.length.toFloat()
                
                segments.forEachIndexed { index, w ->
                    val wStartPct = startOffsetChars / totalChars
                    val wEndPct = (startOffsetChars + w.length) / totalChars

                    val wLeft = parentRect.left + (parentRect.width() * wStartPct).toInt()
                    val wRight = parentRect.left + (parentRect.width() * wEndPct).toInt()

                    val wordBounds = android.graphics.RectF(
                        wLeft.toFloat(),
                        parentRect.top.toFloat(),
                        wRight.toFloat(),
                        parentRect.bottom.toFloat()
                    )
                    
                    words.add(
                        Word(
                            text = w,
                            index = index,
                            startIndex = startOffsetChars,
                            endIndex = startOffsetChars + w.length,
                            bounds = wordBounds
                        )
                    )

                    startOffsetChars += w.length + 1 // +1 for the space (simplified)
                }
                
                if (words.isNotEmpty()) {
                    result.add(
                        TextNode(
                            id = UUID.randomUUID().toString(),
                            fullText = blockText,
                            bounds = parentRect,
                            words = words
                        )
                    )
                }

            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            
            iterator.delete()
            tess.recycle()

            Log.d(TAG, "Tesseract successfully extracted ${result.size} text nodes.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract processing error: ${e.message}")
        }
        
        return@withContext result
    }
}
