package apk.hurnell.recipebookreader.helpers

import android.content.Context
import android.util.Log
import apk.hurnell.recipebookreader.model.Book
import apk.hurnell.recipebookreader.model.BookHistoryItem
import apk.hurnell.recipebookreader.model.BookInfo
import apk.hurnell.recipebookreader.model.BookmarkItem
import apk.hurnell.recipebookreader.model.Category
import apk.hurnell.recipebookreader.model.CategoryItem
import apk.hurnell.recipebookreader.model.FileItem
import apk.hurnell.recipebookreader.model.RecentFile
import apk.hurnell.recipebookreader.model.Row
import apk.hurnell.recipebookreader.model.TocItem
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class PdfRepository(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val dbHelper: DatabaseHelper by lazy { DatabaseHelper(appContext) }

    suspend fun openPdfFast(file: File): Document = withContext(Dispatchers.IO) {
        val tmpFile = File(context.cacheDir, "tmp_${file.name}")
        if (!tmpFile.exists()) {
            context.cacheDir.listFiles()?.forEach { child ->
                try {
                    child.deleteRecursively()
                } catch (e: Exception) {
                    Log.e("CACHE", "Failed to delete ${child.name}")
                }
            }
            try {
                file.inputStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Log.e("CACHE", "Copy failed: ${e.message}")
            }
        }
        Document.openDocument(tmpFile.absolutePath)
    }

    fun loadCategories(): List<Category> = dbHelper.loadCategories()

    fun updateIsAlternateCover(sha: String, isAlternateCover: Int) {
        dbHelper.updateIsAlternateCover(sha, isAlternateCover)
    }

    fun updateBookStringParam(bookId: Long, column: String, value: String) =
        dbHelper.updateBookStringParam(bookId, column, value)

    fun createCategory(name: String): Long {
        return dbHelper.createCategory(name)
    }

    fun updateBookCategory(bookId: Long, bookColumn: String, categoryId: Long) {
        return dbHelper.updateBookCategory(bookId, bookColumn, categoryId)
    }

    suspend fun getOrCreateBook(file: File, path: String, document: Document) =
        withContext(Dispatchers.IO) {
            dbHelper.getOrInsertBook(file, path, document)
        }

    suspend fun getBook(location: String): Book? =
        withContext(Dispatchers.IO) {
            dbHelper.getBook(location)
        }

    suspend fun hasToc(bookId: Long): Boolean =
        withContext(Dispatchers.IO) {
            DatabaseHelper(context).hasTableOfContents(bookId)
        }

    suspend fun generateTocAsync(
        document: Document,
        bookId: Long,
        progressCallback: ((percent: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        dbHelper.generateToc(document, bookId, progressCallback)
    }

    fun getRecentFiles(): List<RecentFile> {
        return dbHelper.getRecentFiles()
    }

    fun getBookInfoForItemPath(path: String): BookInfo? {
        return dbHelper.getBookInfoForItemPath(path)
    }

    fun getUsedCategories(activityName: String?, currentSearchTerm: String): List<CategoryItem> {
        return dbHelper.getUsedCategories(activityName, currentSearchTerm)
    }

    fun getBookShelfBooks(category: String): List<FileItem> {
        return dbHelper.getBookShelfBooks(category)
    }

    fun getFilteredEveryToc(currentText: String, currentCategory: String): MutableList<TocItem> {
        return dbHelper.getFilteredEveryToc(currentText, currentCategory)
    }

    suspend fun updateBookIsbn(bookId: Long, foundIsbn: IsbnResult) {
        return withContext(Dispatchers.IO) {
            DatabaseHelper(context).updateBookIsbn(bookId, foundIsbn)
        }
    }

    fun searchBooks(currentText: String, currentCategory: String): Long {
        return dbHelper.currentQuery(currentText, currentCategory)
    }

    fun getTocRows(bookId: Int): List<Row> {
        return dbHelper.getTocRows(bookId)
    }

    fun createBookmark(item: BookmarkItem): Boolean {
        return dbHelper.createBookmark(item)
    }

    fun updateBookmark(item: BookmarkItem, currentCategory: String): List<BookmarkItem> {
        return dbHelper.updateBookmark(item, currentCategory)
    }

    fun deleteBookmark(item: BookmarkItem): Boolean {
        return dbHelper.deleteBookmark(item)
    }

    fun getBookmarksForBook(bookId: Int): List<BookmarkItem> {
        return dbHelper.getBookmarksForBook(bookId)
    }

    fun getAllBookmarks(currentCategory: String): List<BookmarkItem> {
        return dbHelper.getAllBookmarks(currentCategory)
    }


    fun getBookHistory(bookId: Long): List<BookHistoryItem> {
        return dbHelper.getBookHistory(bookId)
    }

    fun addBookHistoryItem(historyItem: BookHistoryItem): List<BookHistoryItem> {
        return dbHelper.addBookHistoryItem(historyItem)
    }

    fun clearBookHistory(bookId: Long) {
        dbHelper.clearBookHistory(bookId)
    }

    fun updateBookHistoryItem(historyItem: BookHistoryItem): List<BookHistoryItem> {
        return dbHelper.updateBookHistoryItem(historyItem)
    }

    fun removeBookHistoryItem(historyItemId: Long, bookId: Long): List<BookHistoryItem> {
        return dbHelper.removeBookHistoryItem(historyItemId, bookId)
    }

    suspend fun generateBookCoverThumbnail(
        sha: String,
        document: Document,
        bookTitle: String?,
        bookAuthor: String?,
        targetWidth: Int = 200,
        targetHeight: Int = 300,
        overwrite: Boolean = false
    ): Boolean {
        return withContext(Dispatchers.IO) {
            DatabaseHelper(context).generateBookCoverThumbnail(
                sha,
                document,
                bookTitle,
                bookAuthor,
                targetWidth,
                targetHeight,
                overwrite
            )
        }
    }

    fun getSubsequentTocItem(
        page: Int,
        lastTokId: Long?,
        up: Boolean,
        currentBookId: Long
    ): TocItem? {
        return dbHelper.getSubsequentTocItem(page, lastTokId, up, currentBookId)
    }

    fun deleteBook(currentBookId: Long): Boolean {
        return dbHelper.deleteBook(currentBookId)
    }

    fun setTocUnavailable(bookId: Long): Boolean {
        return dbHelper.setTocUnavailable(bookId)
    }

    fun getTocUnavailable(bookId: Long): Boolean {
        return dbHelper.getTocUnavailable(bookId)
    }

    fun updateVolumeTitleStatus(bookId: Long, checked: Boolean): Boolean {
        return dbHelper.updateVolumeTitleStatus(bookId, checked)
    }

    fun getChartConversion(ingredient: String?): String {
        return dbHelper.getChartConversion(ingredient)
    }

    fun normalizeText(title: String): String {
        return dbHelper.normalizeText(title)
    }
}
