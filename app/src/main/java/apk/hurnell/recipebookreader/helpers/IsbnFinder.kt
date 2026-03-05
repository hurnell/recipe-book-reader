package apk.hurnell.recipebookreader.helpers

import android.util.Log
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Page

class IsbnFinder {
    companion object {
        private const val LOG_TAG = "NIGEL_HURNELL"

        private val ISBN_REGEX = Regex(
            "(?i)ISBN(?:-1[03])?:?\\s?((?:97[89][-\\s]?)?[0-9][-\\s]?(?:[0-9][-\\s]?){1,8}[0-9][-\\s]?(?:[0-9][-\\s]?){1,8}[0-9X])"
        )
    }

    fun findIsbnInDocument(doc: Document): String? {
        val pagesToScan = mutableListOf<Int>()
        for (i in 0 until doc.countPages()) {
            pagesToScan.add(i)
        }

        if (doc.countPages() > 10) {
            pagesToScan.add(doc.countPages() - 1)
        }

        for (pageIndex in pagesToScan) {
            try {
                val page = doc.loadPage(pageIndex)
                val searchHits = page.search("ISBN");
                if (searchHits != null && searchHits.size > 0) {
                    val d = 123
                }
                val text = extractTextFromPage(page)

                val match = ISBN_REGEX.find(text)
                if (match != null) {
                    for (i in 1 until match.groupValues.size) {
                        val candidate = match.groupValues[i].trim()

                        if (isValidIsbnFormat(candidate)) {
                            return candidate.replace(" ", "")
                        }
                    }
                }
                if ("ISBN" in text) {
                    val a = 123
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error scanning page $pageIndex", e)
            }
        }
        return null
    }

    private fun isValidIsbnFormat(text: String): Boolean {
        val clean = text.replace("-", "").replace(" ", "")
        if (clean.length != 10 && clean.length != 13) return false

        return text.matches(Regex("[0-9xX\\-\\s]+"))
    }

    private fun extractTextFromPage(page: Page): String {
        val st = page.toStructuredText("preserve-whitespace")
        val content = st.asText()
        st.destroy()
        return content
    }
}