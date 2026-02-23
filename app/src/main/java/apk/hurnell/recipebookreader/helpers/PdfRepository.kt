package apk.hurnell.recipebookreader.helpers

import android.content.ContentValues
import android.content.Context
import androidx.core.database.sqlite.transaction
import apk.hurnell.recipebookreader.model.Book
import apk.hurnell.recipebookreader.model.Category
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfRepository(
    private val context: Context
) {
    private var ignoreTocParams = false
    private var ignoredOffset: Float? = null
    private var ignoredScale: Float? = null
    private var ignoredTranslate: Float? = null


    suspend fun openPdfFast(file: File): Document = withContext(Dispatchers.IO) {
        val tmpFile = File(context.cacheDir, "tmp_${file.name}")
        if (!tmpFile.exists()) {
            file.inputStream().use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        Document.openDocument(tmpFile.absolutePath)
    }

    fun loadCategories(): List<Category> {
        return DatabaseHelper(context).loadCategories()
    }

    fun setIgnoreTocParams() {
        ignoreTocParams = true
    }

    fun updateBookStringParam(bookId: Long, column: String, value: String): Boolean {
        return DatabaseHelper(context).updateBookStringParam(bookId, column, value)
    }

    fun createCategory(name: String): Long {
        return DatabaseHelper(context).createCategory(name)
    }
    fun updateBookCategory(bookId: Long, bookColumn: String, categoryId: Long) {
        return DatabaseHelper(context).updateBookCategory(bookId, bookColumn,  categoryId)
    }
    suspend fun getOrCreateBook(file: File, path: String, document: Document, opened: Boolean): Book? =
        withContext(Dispatchers.IO) {
            DatabaseHelper(context).getOrInsertBook(file, path, document, opened)
        }

    suspend fun hasToc(bookId: Long): Boolean =
        withContext(Dispatchers.IO) {
            DatabaseHelper(context).hasTableOfContents(bookId)
        }

    suspend fun generateTocAsync(
        document: Document,
        bookId: Long,
        progressCallback: ((percent: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {

        val outline = document.loadOutline() ?: return@withContext false

        val dbHelper = DatabaseHelper(context)
        val db = dbHelper.openDatabase()

        val flatList = flattenOutline(outline)
        val total = flatList.size
        var processed = 0

        fun insertWithProgress(
            entries: List<com.artifex.mupdf.fitz.Outline>,
            parentId: Long?,
            level: Int
        ) {
            val stmt = db.compileStatement(
                """
                INSERT INTO toc (book_id_fk, parent_id, level, title, page, `offset`, scale, translate)
                VALUES (?,?,?,?,?,?,?,?)
            """.trimIndent()
            )
            entries.forEach { entry ->

                val page = dbHelper.extractPageFromUri(entry.uri)
                val pageCoordinates =
                    FunctionalStructuredTextWalker().getPageCoordinates(
                        document,
                        page - 1,
                        ignoreTocParams
                    )
                if (!ignoreTocParams) {
                    ignoredOffset = ignoredOffset?.let { minOf(it, pageCoordinates.leftOffset) }
                        ?: pageCoordinates.leftOffset
                    ignoredScale = ignoredScale?.let { minOf(it, pageCoordinates.targetScale) }
                        ?: pageCoordinates.targetScale
                    ignoredTranslate =
                        ignoredTranslate?.let { minOf(it, pageCoordinates.translatingPercentage) }
                            ?: pageCoordinates.translatingPercentage
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
                val rowId = stmt.executeInsert()
                processed++
                progressCallback?.invoke((processed * 100) / total)

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