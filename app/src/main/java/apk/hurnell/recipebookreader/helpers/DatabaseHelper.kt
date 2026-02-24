package apk.hurnell.recipebookreader.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.sqlite.transaction
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import com.google.gson.Gson
import apk.hurnell.recipebookreader.model.Book
import apk.hurnell.recipebookreader.model.Category
import apk.hurnell.recipebookreader.model.RecentFile
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import androidx.core.graphics.createBitmap


class DatabaseHelper(private val context: Context) {
    companion object {
        private const val DB_NAME = "recipe-reader.db"
        private val gson = Gson()
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    fun saveConfiguration(key: String, data: Any) {
        val db = openDatabase()
        val jsonString = gson.toJson(data)
        db.transaction {
            val values = ContentValues().apply {
                put("key", key)
                put("json", jsonString)
            }

            val rowsAffected = db.update("configuration", values, "key = ?", arrayOf(key))

            if (rowsAffected == 0) {
                db.insert("configuration", null, values)
            }
        }
    }

    fun <T> getConfiguration(key: String, clazz: Class<T>): T? {
        val db = openDatabase()
        db.query(
            "configuration",
            arrayOf("json"),
            "key = ?",
            arrayOf(key),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val json = cursor.getString(0)
                return gson.fromJson(json, clazz)
            }
        }
        return null
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

    fun getOrInsertBook(file: File, path: String, document: Document, opened: Boolean): Book? {
        val bookId = checkAddBookToDatabase(file, path, document, opened)
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
                    category = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("category")),
                    subCategory = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("sub_category")),
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
        val db = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )

        val cursor = db.rawQuery("PRAGMA journal_mode=WAL;", null)
        cursor.use {
            if (it.moveToFirst()) {
                Log.d(LOG_TAG, "Journal mode set to: ${it.getString(0)}")
            }
        }
        return db
    }

    fun extractPageFromUri(uri: String?): Int {
        if (uri.isNullOrEmpty()) return 0

        val regex = """page=(\d+)""".toRegex()
        val match = regex.find(uri)
        return match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    }


    private fun countOutlineEntries(outlineArray: Array<Outline>): Int {
        var count = 0
        outlineArray.forEach { entry ->
            count++
            if (!entry.down.isNullOrEmpty()) {
                count += countOutlineEntries(entry.down)
            }
        }
        return count
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
        val start = System.currentTimeMillis()
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
            val then = System.currentTimeMillis()
            val elapsed = then - start
            Log.d(
                LOG_TAG,
                "Inserted: ${entry.title} at level $level which took $elapsed ms = ${elapsed / 1000} secs"
            )
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

    fun checkAddBookToDatabase(
        file: File,
        path: String,
        document: Document,
        opened: Boolean
    ): Long {
        val db = openDatabase()

        return db.query("books", arrayOf("id"), "location = ?", arrayOf(path), null, null, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val bookId = cursor.getLong(0)
                    if (opened) {
                        val values = ContentValues().apply {
                            put("last_opened", System.currentTimeMillis())
                        }
                        db.update("books", values, "id = ?", arrayOf(bookId.toString()))
                    }
                    bookId
                } else {
                    val sha =  file.sha256()
                    val values = ContentValues().apply {
                        put("name", document.getMetaData(Document.META_INFO_TITLE) ?: file.name)
                        put("location", path)
                        put("sha", sha)
                        put("author", document.getMetaData(Document.META_INFO_AUTHOR) ?: "Unknown")
                        if (opened) {
                            put("last_opened", System.currentTimeMillis())
                        }
                        put("toc_created", 0)
                    }

                    val newId = db.insert("books", null, values)

                    if (newId == -1L) {
                        Log.e(
                            LOG_TAG,
                            "Failed to insert book! Check for column name mismatches."
                        )
                    } else {
                        generateBookCoverThumbnail(context, sha, document)
                    }
                    newId
                }
            }
    }
    fun generateBookCoverThumbnail(
        context: Context,
        sha: String,
        document: Document,
        targetWidth: Int = 200,
        targetHeight: Int = 300
    ): Boolean {
        return try {
            val hashName = "${sha}.png"
            val thumbnailFile = File(context.filesDir, hashName)
            if (thumbnailFile.exists()) return true
            val page = document.loadPage(0)
            val bounds = page.bounds
            val pageWidth = bounds.x1 - bounds.x0
            val pageHeight = bounds.y1 - bounds.y0
            val scaleX = targetWidth / pageWidth
            val scaleY = targetHeight / pageHeight
            val scale = minOf(scaleX, scaleY)
            val bitmap = createBitmap((pageWidth * scale).toInt(), (pageHeight * scale).toInt())
            val device = AndroidDrawDevice(bitmap, 0, 0)
            page.run(device, Matrix(scale, scale), null)

            device.close()
            device.destroy()
            page.destroy()
            FileOutputStream(thumbnailFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

    fun loadCategories(): List<Category> {
        val list = mutableListOf<Category>()
        val db = openDatabase()
        val cursor = db.rawQuery(
            "SELECT id, category FROM categories ORDER BY category",
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Category(
                        id = it.getLong(0),
                        category = it.getString(1)
                    )
                )
            }
        }
        return list
    }

    fun createCategory(category: String): Long {
        val db = openDatabase()
        val insertSql = """
            INSERT INTO categories (category)
            VALUES (?)
        """.trimIndent()
        val stmt = db.compileStatement(insertSql)
        stmt.bindString(1, category)
        val rowId = stmt.executeInsert()
        stmt.close()
        return rowId
    }

    fun updateBookCategory(bookId: Long, bookColumn: String, categoryId: Long) {
        val db = openDatabase()
        val values = ContentValues().apply {
            put(bookColumn, categoryId)
        }
        db.update("books", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun getRecentFiles(): List<RecentFile> {
        val db = openDatabase()
        val list = mutableListOf<RecentFile>()
        val cursor = db.rawQuery(
            "SELECT location FROM books ORDER BY (last_opened IS NULL) ASC, last_opened DESC",
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    RecentFile(
                        location = it.getString(0),
                        lastOpened = it.getLongOrNull(1)
                    )
                )
            }
        }
        return list
    }

    fun getUsedCategories(): List<String> {
        val db = openDatabase()
        val list = mutableListOf<String>()
        val sql = """
            SELECT c.category AS used_categories
            FROM books AS b
            LEFT JOIN categories AS c
              ON c.id = b.category
            WHERE c.category IS NOT NULL
            
            UNION
            
            SELECT sc.category
            FROM books AS b
            LEFT JOIN categories AS sc
              ON sc.id = b.sub_category
            WHERE sc.category IS NOT NULL
            
            ORDER BY used_categories
        """.trimIndent()
        val cursor = db.rawQuery(
            sql,
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }
}
