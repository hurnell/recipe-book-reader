package apk.hurnell.recipebookreader.helpers

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredTextWalker
import kotlin.math.max
import kotlin.math.min

data class PageCoordinates(
    var minX: Float = 1000000.0f,
    var maxX: Float = 0.0f,
    var width: Float = 0.0f,
    var height: Float = 0.0f,
    var leftOffset: Float = 0.0f,
    var hasImages: Boolean = false,
    var hasText: Boolean = false,
    var targetScale: Float = 0.0f,
    var translatingPercentage: Float = 0.0f,
    var found: Boolean = false

) {
    fun populate() {
        if (found) {
            val currentWidth = maxX - minX
            if (currentWidth != 0f) {
                leftOffset = minX
                val rightOffset = width - maxX
                translatingPercentage = leftOffset / (leftOffset + rightOffset)
                targetScale = (width / currentWidth) * 0.95f
            }
        }
    }

    fun intercept(offset: Float?, scale: Float?, percentage: Float?) {
        offset?.let { leftOffset = it }
        scale?.let { targetScale = it }
        percentage?.let { translatingPercentage = it }
    }
}

class FunctionalStructuredTextWalker {

    fun getPageCoordinates(document: Document, pageNumber: Int, ignoreParams: Boolean = false): PageCoordinates {
        val pageCoordinates = PageCoordinates()
        if(ignoreParams) {
            return pageCoordinates
        }
        val page = document.loadPage(pageNumber)
        val bounds = page.bounds
        pageCoordinates.width = bounds.x1 - bounds.x0
        pageCoordinates.height = bounds.y1 - bounds.y0
        val st = page.toStructuredText("preserve-images,preserve-whitespace")
        st.walk(object : StructuredTextWalker {
            override fun onImageBlock(bbox: Rect, matrix: Matrix?, image: Image?) {
                pageCoordinates.minX = min(pageCoordinates.minX, bbox.x0)
                pageCoordinates.maxX = max(pageCoordinates.maxX, bbox.x1)
                pageCoordinates.found = true
                pageCoordinates.hasImages = true
            }

            override fun beginTextBlock(bbox: Rect) {
                pageCoordinates.minX = min(pageCoordinates.minX, bbox.x0)
                pageCoordinates.maxX = max(pageCoordinates.maxX, bbox.x1)
                pageCoordinates.found = true
                pageCoordinates.hasText = true
            }

            override fun endTextBlock() {}
            override fun onChar(
                c: Int,
                origin: Point?,
                font: Font?,
                size: Float,
                quad: Quad?,
                argb: Int,
                flags: Int
            ) {
            }

            override fun beginLine(bbox: Rect?, wmode: Int, dir: Point?) {}
            override fun endLine() {}
            override fun beginStruct(standard: String?, raw: String?, index: Int) {}
            override fun endStruct() {}
            override fun onVector(bbox: Rect?, info: StructuredTextWalker.VectorInfo?, argb: Int) {}
        })
        pageCoordinates.populate()
        st.destroy()
        page.destroy()
        return pageCoordinates
    }
}