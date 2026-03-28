package apk.hurnell.recipebookreader.helpers

import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.artifex.mupdf.fitz.Rect
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class Coordinates(
    val offset: Float,
    val scale: Float,
    val percentage: Float
)

data class SearchResult(
    val pageIndex: Int,
    val text: String,
    val blockRectangles: List<Rect>,
    val coordinates: Coordinates
)

class PdfSearchHelper(private val document: Document) {

    fun search(query: String, startPage: Int = 0): Flow<SearchResult> = flow {
        val pageCount = document.countPages()

        for (i in 0 until pageCount) {
            val currentIndex = (startPage + i) % pageCount

            val result = processPage(currentIndex, query)

            if (result != null) {
                emit(result)
            }
        }
    }

    private suspend fun processPage(pageIndex: Int, query: String): SearchResult? =
        withContext(Dispatchers.Default) {
            val page = document.loadPage(pageIndex)
            try {
                val structuredText = page.toStructuredText()
                val matchingLines = mutableListOf<String>()
                val foundRectangles = mutableListOf<Rect>()

                var minX = 1000000f
                var maxX = 0f
                val bounds = page.bounds
                val width = bounds.x1 - bounds.x0
                structuredText.blocks?.forEach { block ->
                    block.lines?.forEach { line ->
                        val lineBuilder = StringBuilder()
                        line.chars?.forEach { char -> lineBuilder.append(char.c.toChar()) }
                        val lineText = lineBuilder.toString().trim()
                        val normalizedLine = lineText
                            .replace("’", "'")
                            .replace("‘", "'")
                            .replace("“", "\"")
                            .replace("”", "\"")
                        if (normalizedLine.contains(query, ignoreCase = true)) {
                            matchingLines.add(lineText)
                            foundRectangles.add(line.bbox)
                        }
                    }
                    minX = min(minX, block.bbox.x0)
                    maxX = max(maxX, block.bbox.x1)
                }

                if (foundRectangles.isEmpty()) return@withContext null

                val rightOffset = width - maxX
                var percentage = minX / (minX + rightOffset)
                var scale = (width / (maxX - minX)) * 0.95f
                if (percentage.isNaN() || percentage == 0.0f) {
                    percentage = 0.0f
                    scale = 1.0f
                    minX = 0.0f
                }
                val coordinates = Coordinates(minX, scale, percentage)
                val snippet = matchingLines.joinToString(" ... ")
                SearchResult(pageIndex, snippet, foundRectangles, coordinates)

            } finally {
                page.destroy()
            }
        }

}