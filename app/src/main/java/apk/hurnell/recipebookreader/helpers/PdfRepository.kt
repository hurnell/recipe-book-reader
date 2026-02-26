package apk.hurnell.recipebookreader.helpers

import android.content.Context
import android.util.Log
import apk.hurnell.recipebookreader.LastFolderRequested
import apk.hurnell.recipebookreader.model.Book
import apk.hurnell.recipebookreader.model.BookInfo
import apk.hurnell.recipebookreader.model.Category
import apk.hurnell.recipebookreader.model.FileItem
import apk.hurnell.recipebookreader.model.RecentFile
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

    fun saveConfiguration(key: String, json: Any) {
        dbHelper.saveConfiguration("FileBrowserActivityDirectory", json)
    }

    fun getLastDirectory(): File? {
        val saved = dbHelper.getConfiguration(
            "FileBrowserActivityDirectory",
            LastFolderRequested::class.java
        )
        return saved?.let { File(it.directory) }
    }

    fun loadCategories(): List<Category> = dbHelper.loadCategories()

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
        progressCallback: ((percent: Int) -> Unit)? = null,
        ignoreTocParams: Boolean = false
    ) = withContext(Dispatchers.IO) {
        dbHelper.generateToc(document, bookId, progressCallback, ignoreTocParams)
    }

    fun getRecentFiles(): List<RecentFile> {
        return dbHelper.getRecentFiles()
    }

    fun getBookInfoForItemPath(path: String): BookInfo? {
        return dbHelper.getBookInfoForItemPath(path)
    }

    fun getUsedCategories(): Collection<String> {
        return dbHelper.getUsedCategories()
    }

    fun getBookShelfBooks(): List<FileItem> {
        return dbHelper.getBookShelfBooks()
    }

    fun getFilteredEveryToc(currentText: String, currentCategory: String): MutableList<TocItem> {
        return dbHelper.getFilteredEveryToc(currentText, currentCategory)
    }

    suspend fun updateBookIsbn(bookId: Long, foundIsbn: String) {
        return withContext(Dispatchers.IO) {
            DatabaseHelper(context).updateBookIsbn(bookId, foundIsbn)
        }
    }
}