package apk.hurnell.recipebookreader.helpers

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.core.database.sqlite.transaction
import apk.hurnell.recipebookreader.model.Book
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfRepository(
    private val contentResolver: ContentResolver,
    private val context: Context
) {
    private var ignoreTocParams = false
    private var ignoredOffset: Float? = null
    private var ignoredScale: Float? = null
    private var ignoredTranslate: Float? = null
    suspend fun openDocument(uri: Uri): Document =
        withContext(Dispatchers.IO) {
            val stream = PdfStreamer(contentResolver, uri)
            Document.openDocument(stream, "application/pdf")
        }
    fun setIgnoreTocParams(){
        ignoreTocParams = true
    }
    suspend fun checkOrCreateBook(
        file: File,
        path: String,
        document: Document
    ): Long = withContext(Dispatchers.IO) {
        DatabaseHelper(context).checkAddBookToDatabase(file, path, document)
    }
    suspend fun getBook(
        file: File,
        path: String,
        document: Document
    ): Book? = withContext(Dispatchers.IO) {
        DatabaseHelper(context).getOrInsertBook(file, path, document)
    }


    suspend fun hasToc(bookId: Long): Boolean =
        withContext(Dispatchers.IO) {
            DatabaseHelper(context).hasTableOfContents(bookId)
        }

    suspend fun generateTocAsync(
        pdfUri: Uri,
        bookId: Long,
        sizeInBytes: Long,
        progressCallback: ((percent: Int, lastInsertMs: Long, total: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {

        val document = openDocument(pdfUri)
        val outline = document.loadOutline() ?: return@withContext false

        val dbHelper = DatabaseHelper(context)
        val db = dbHelper.openDatabase()

        val flatList = flattenOutline(outline)
        val total = flatList.size
        var processed = 0

        fun insertWithProgress(entries: List<com.artifex.mupdf.fitz.Outline>, parentId: Long?, level: Int) {
            val stmt = db.compileStatement("""
                INSERT INTO toc (book_id_fk, parent_id, level, title, page, `offset`, scale, translate, has_images, has_text, position_ignored)
                VALUES (?,?,?,?,?,?,?,?, ?, ? ,?)
            """.trimIndent())
            var lastTime = System.currentTimeMillis()
            entries.forEach { entry ->
                val currentTime = System.currentTimeMillis()
                val delta = currentTime - lastTime
                lastTime = currentTime

                val page = dbHelper.extractPageFromUri(entry.uri)
                val pageCoordinates =
                    FunctionalStructuredTextWalker().getPageCoordinates(document, page - 1, ignoreTocParams)
                if (!ignoreTocParams) {
                    ignoredOffset = ignoredOffset?.let { minOf(it, pageCoordinates.leftOffset) } ?: pageCoordinates.leftOffset
                    ignoredScale = ignoredScale?.let { minOf(it, pageCoordinates.targetScale) } ?: pageCoordinates.targetScale
                    ignoredTranslate = ignoredTranslate?.let { minOf(it, pageCoordinates.translatingPercentage) } ?: pageCoordinates.translatingPercentage
                } else {
                    pageCoordinates.intercept(ignoredOffset, ignoredScale, ignoredTranslate)
                }
                stmt.clearBindings()
                stmt.bindLong(1, bookId)
                parentId?.let { stmt.bindLong(2, it) } ?: stmt.bindNull(2)
                stmt.bindLong(3, level.toLong())
                stmt.bindString(4, entry.title ?: "")
                stmt.bindLong(5, page.toLong())
                stmt.bindDouble(6, pageCoordinates.leftOffset.toDouble())
                stmt.bindDouble(7, pageCoordinates.targetScale.toDouble())
                stmt.bindDouble(8, pageCoordinates.translatingPercentage.toDouble())
                stmt.bindLong(9, if (pageCoordinates.hasImages) 1L else 0L)
                stmt.bindLong(10, if (pageCoordinates.hasText) 1L else 0L)
                val ignored = if (pageCoordinates.found) 0L else 1L
                stmt.bindLong(11, ignored)
                val rowId = stmt.executeInsert()
                processed++
                progressCallback?.invoke((processed * 100) / total, delta, total)

                if (!entry.down.isNullOrEmpty()) {
                    insertWithProgress(entry.down.toList(), rowId, level + 1)
                }
            }
            stmt.close()
        }

        db.transaction {
            insertWithProgress(outline.toList(), null, 0)
        }

        val values = ContentValues().apply { put("toc_created", 1) }
        db.update("books", values, "id = ?", arrayOf(bookId.toString())) > 0
    }

    private fun flattenOutline(outline: Array<com.artifex.mupdf.fitz.Outline>): List<com.artifex.mupdf.fitz.Outline> {
        val result = mutableListOf<com.artifex.mupdf.fitz.Outline>()
        fun recurse(entries: Array<com.artifex.mupdf.fitz.Outline>) {
            entries.forEach {
                result.add(it)
                if (!it.down.isNullOrEmpty()) recurse(it.down)
            }
        }
        recurse(outline)
        return result
    }
}