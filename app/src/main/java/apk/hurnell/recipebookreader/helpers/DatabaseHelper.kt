package apk.hurnell.recipebookreader.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import androidx.core.database.sqlite.transaction
import apk.hurnell.recipebookreader.model.Book


class DatabaseHelper(private val context: Context) {
    companion object {
        private const val DB_NAME = "recipe-reader.db"

        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    @Throws(IOException::class)
    fun copyDatabaseIfNeeded() {
        val dbFile: File = context.getDatabasePath(DB_NAME)

        if (!dbFile.exists()) {
            Log.d(LOG_TAG, "Database not found, copying from assets...")

            dbFile.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }

            try {
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(LOG_TAG, "Database copied successfully.")
            } catch (e: IOException) {
                Log.e(LOG_TAG, "FAILED to copy database: ${e.message}")
                throw e
            }
        }
    }

    fun getOrInsertBook(file: File, path: String, document: Document): Book? {
        val bookId = checkAddBookToDatabase(file, path, document)
        val db = openDatabase()

        return db.query(
            "books",
            arrayOf(
                "id", "sha", "name", "location", "author",
                "last_opened", "toc_created", "toc_unavailable",
                "category", "sub_category", "alternate_cover"
            ),
            "id = ?",
            arrayOf(bookId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Book(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    sha = cursor.getString(cursor.getColumnIndexOrThrow("sha")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    lastOpened = cursor.getLongOrNull(cursor.getColumnIndexOrThrow("last_opened")),
                    tocCreated = cursor.getLongOrNull(cursor.getColumnIndexOrThrow("toc_created")),
                    tocUnavailable = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("toc_unavailable")),
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    subCategory = cursor.getString(cursor.getColumnIndexOrThrow("sub_category")),
                    alternateCover = cursor.getString(cursor.getColumnIndexOrThrow("alternate_cover"))
                )
            } else null
        }
    }

    fun openDatabase(): SQLiteDatabase {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            copyDatabaseIfNeeded()
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
    }

    fun extractPageFromUri(uri: String?): Int {
        if (uri.isNullOrEmpty()) return 0

        val regex = """page=(\d+)""".toRegex()
        val match = regex.find(uri)
        return match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    }


    fun insertOutline(
        document: Document,
        db: SQLiteDatabase,
        bookId: Long,
        outlineArray: Array<Outline>
    ) {
        db.transaction {
            try {

                performRecursiveInsert(document, this, bookId, outlineArray, null, 0)
                Log.i(LOG_TAG, "TOC Transaction Successful for book $bookId")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "TOC Insert failed: ${e.message}")
            } finally {
            }
        }
    }

    private fun performRecursiveInsert(
        document: Document,
        db: SQLiteDatabase,
        bookId: Long,
        outlineArray: Array<Outline>,
        parentId: Long?,
        level: Int
    ) {
        val insertSql = """
        INSERT INTO toc (book_id_fk, parent_id, level, title, page, `offset`, scale, translate)
        VALUES (?,?,?,?,?,?,?,?)
    """.trimIndent()

        val stmt = db.compileStatement(insertSql)

        outlineArray.forEach { entry ->
            val page = extractPageFromUri(entry.uri)
            val pageCoordinates =
                FunctionalStructuredTextWalker().getPageCoordinates(document, page - 1)

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
            Log.d(LOG_TAG, "Inserted: ${entry.title} at level $level")

            if (!entry.down.isNullOrEmpty()) {
                performRecursiveInsert(document, db, bookId, entry.down, rowId, level + 1)
            }
        }
        stmt.close()
    }


    private fun iterateOutline(document: Document, outlineArray: Array<Outline>, level: Int = 0) {
        outlineArray.forEach { entry ->
            val page = extractPageFromUri(entry.uri)
            val pageCoordinates =
                FunctionalStructuredTextWalker().getPageCoordinates(document, page - 1)

            Log.e(
                LOG_TAG,
                "level: $level title: ${entry.title} page: $page leftOffset ${pageCoordinates.leftOffset} targetScale: ${pageCoordinates.targetScale}"
            )
            Log.e(
                LOG_TAG,
                "translatingPercentage: ${pageCoordinates.translatingPercentage} found: ${pageCoordinates.found}"
            )
            if (entry.down != null && entry.down.isNotEmpty()) {
                iterateOutline(document, entry.down, level + 1)
            }
        }
    }

    fun generateTOC(document: Document?, bookId: Long): Boolean {
        if (document == null) {
            Log.e(LOG_TAG, "Cannot generate TOC: Document is null")
            return false
        }
        var success = false
        val outline = try {
            document.loadOutline()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error loading outline: ${e.message}")
            null
        }

        if (outline != null) {
            val db = openDatabase()

            insertOutline(document, db, bookId, outline)

            val values = ContentValues().apply {
                put("toc_created", 1)
            }
            val rowsUpdated = db.update("books", values, "id = ?", arrayOf(bookId.toString()))

            if (rowsUpdated > 0) {
                success = true
                Log.i(LOG_TAG, "Successfully updated toc_created flag for book $bookId")
            } else {
                success = false
                Log.e(LOG_TAG, "Failed to update toc_created flag for book $bookId")
            }
        } else {
            val db = openDatabase()
            val values = ContentValues().apply {
                put("toc_unavailable", 1)
            }
            val rowsUpdated = db.update("books", values, "id = ?", arrayOf(bookId.toString()))
            if (rowsUpdated > 0) {
                success = false
                Log.i(LOG_TAG, "Successfully updated toc_unavailable flag for book $bookId")
            } else {
                success = false
                Log.e(LOG_TAG, "Failed to update toc_unavailable flag for book $bookId")
            }
            Log.e(
                LOG_TAG,
                "loadOutline returned null. Page count: ${document.countPages()}"
            )
        }
        return success
    }

    fun updateBookStringParam(bookId: Long, column: String, value: String): Boolean {
        val db = openDatabase()
        val values = ContentValues().apply {
            put(column, value)
        }
        val rowsUpdated = db.update("books", values, "id = ?", arrayOf(bookId.toString()))
        return rowsUpdated == 1
    }

    fun hasTableOfContents(bookId: Long): Boolean {
        val db = openDatabase()
        return db.query(
            "books",
            arrayOf("toc_created"),
            "id = ?",
            arrayOf(bookId.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0) == 1
            } else {
                false
            }
        }
    }

    fun checkAddBookToDatabase(file: File, path: String, document: Document): Long {
        val db = openDatabase()

        return db.query("books", arrayOf("id"), "location = ?", arrayOf(path), null, null, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    val values = ContentValues().apply {
                        put("name", document.getMetaData(Document.META_INFO_TITLE) ?: file.name)
                        put("location", path)
                        put("sha", file.sha256())
                        put("author", document.getMetaData(Document.META_INFO_AUTHOR) ?: "Unknown")
                        put("last_opened", System.currentTimeMillis())
                        put("toc_created", 0)
                    }

                    val newId = db.insert("books", null, values)

                    if (newId == -1L) {
                        Log.e(
                            LOG_TAG,
                            "Failed to insert book! Check for column name mismatches."
                        )
                    }
                    newId
                }
            }
    }


    fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { fis ->
            val buffer = ByteArray(1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
