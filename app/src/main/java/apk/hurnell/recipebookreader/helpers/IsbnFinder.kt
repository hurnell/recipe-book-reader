package apk.hurnell.recipebookreader.helpers

import android.util.Log
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Page

data class IsbnResult(
    val isbn: String,
    val totalLines: Int,
    val scanned: Boolean = false
)

class IsbnFinder {
    companion object {
        private const val LOG_TAG = "NIGEL_HURNELL"

        private val ISBN_REGEX = Regex(
            "(?i)ISBN(?:-1[03])?:?\\s?((?:97[89][-\\s]?)?[0-9][-\\s]?(?:[0-9][-\\s]?){1,8}[0-9][-\\s]?(?:[0-9][-\\s]?){1,8}[0-9X])"
        )
    }

    fun findIsbnInDocument(doc: Document): IsbnResult {
        val pagesToScan = mutableListOf<Int>()
        val totalPages = doc.countPages()
        for (i in 0 until totalPages) {
            pagesToScan.add(i)
        }
        var isbn = ""
        var totalLines = 0
        for (pageIndex in pagesToScan) {
            try {
                val page = doc.loadPage(pageIndex)
                val result = extractTextFromPage(page)
                totalLines += result.totalLines
                val match = ISBN_REGEX.find(result.isbn)
                if (match != null) {
                    for (i in 1 until match.groupValues.size) {
                        val candidate = match.groupValues[i].trim()
                        if (isValidIsbnFormat(candidate)) {
                            isbn = candidate.replace(" ", "")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error scanning page $pageIndex", e)
            }
        }
        val scanned = totalLines < 2 * totalPages
        return IsbnResult(
            isbn,
            totalLines,
            scanned
        )
    }

    private fun isValidIsbnFormat(text: String): Boolean {
        val clean = text.replace("-", "").replace(" ", "")
        if (clean.length != 10 && clean.length != 13) return false

        return text.matches(Regex("[0-9xX\\-\\s]+"))
    }

    private fun extractTextFromPage(page: Page): IsbnResult {
        val st = page.toStructuredText("preserve-whitespace")
        val content = st.asText()
        val totalLines = st.blocks.sumOf { it.lines.size }
        st.destroy()

        return IsbnResult(
            content,
            totalLines
        )
    }
}