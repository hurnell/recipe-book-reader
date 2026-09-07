package apk.hurnell.recipebookreader.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import apk.hurnell.recipebookreader.model.Book
import apk.hurnell.recipebookreader.model.Category
import apk.hurnell.recipebookreader.model.RecentFile
import com.artifex.mupdf.fitz.Matrix
import android.graphics.Matrix as GraphicsMatrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import apk.hurnell.recipebookreader.model.BookInfo
import apk.hurnell.recipebookreader.model.FileItem
import androidx.core.graphics.createBitmap
import apk.hurnell.recipebookreader.model.BaseBookmarkTocItem
import apk.hurnell.recipebookreader.model.BookHistoryItem
import apk.hurnell.recipebookreader.model.BookmarkItem
import apk.hurnell.recipebookreader.model.CategoryItem
import apk.hurnell.recipebookreader.model.RecentRecipeItem
import apk.hurnell.recipebookreader.model.Row
import apk.hurnell.recipebookreader.model.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 4) {

    companion object {
        private const val DB_NAME = "recipe-reader.db"
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Not used because we copy prebuilt DB from assets
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToVersion2(db)
        if (oldVersion < 3) migrateToVersion3(db)
        if (oldVersion < 4) migrateToVersion4(db)
    }

    private fun migrateToVersion4(db: SQLiteDatabase) {
        // A prior bug in addRecentRecipeItem() wrote the recent_recipes row id into
        // toc.bookmark_id whenever a recipe was opened, mislabeling unbookmarked toc
        // entries as bookmarked. Clear any bookmark_id that doesn't reference a real bookmark.
        try {
            db.execSQL("UPDATE toc SET bookmark_id = NULL WHERE bookmark_id IS NOT NULL AND bookmark_id NOT IN (SELECT id FROM bookmarks)")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to clear orphaned toc.bookmark_id values: ${e.message}", e)
        }
    }

    private fun migrateToVersion3(db: SQLiteDatabase) {
        // Pages where no text/image bbox was found got persisted with scale=0/translate=0
        // (see FunctionalStructuredTextWalker.PageCoordinates), which collapses the page
        // draw matrix to a point at render time. Backfill those rows to the "unset" default.
        try {
            db.execSQL("UPDATE toc SET scale = 1.0, translate = 0.0 WHERE scale <= 0")
            db.execSQL("UPDATE bookmarks SET scale = 1.0, translate = 0.0 WHERE scale <= 0")
            db.execSQL("UPDATE recent_recipes SET scale = 1.0, translate = 0.0 WHERE scale <= 0")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to backfill degenerate scale=0 rows: ${e.message}", e)
        }
    }

    private fun migrateToVersion2(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS book_category_map (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id_fk INTEGER NOT NULL,
                category_id_fk INTEGER NOT NULL,
                is_main INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_book_category_unique ON book_category_map(book_id_fk, category_id_fk)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_book_category_one_main ON book_category_map(book_id_fk) WHERE is_main = 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_category_book ON book_category_map(book_id_fk)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_category_category ON book_category_map(category_id_fk)")

        try {
            db.execSQL(
                """
                INSERT OR IGNORE INTO book_category_map (book_id_fk, category_id_fk, is_main)
                SELECT id, category, 1 FROM books WHERE category IS NOT NULL
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO book_category_map (book_id_fk, category_id_fk, is_main)
                SELECT id, sub_category, 0 FROM books WHERE sub_category IS NOT NULL
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to backfill legacy category columns (already migrated?): ${e.message}", e)
        }

        try {
            db.execSQL("ALTER TABLE books DROP COLUMN category")
            db.execSQL("ALTER TABLE books DROP COLUMN sub_category")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to drop legacy category columns: ${e.message}", e)
        }
    }

    override fun getWritableDatabase(): SQLiteDatabase {
        copyDatabaseIfNeeded()
        return super.getWritableDatabase()
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        copyDatabaseIfNeeded()
        return super.getReadableDatabase()
    }

    @Throws(IOException::class)
    fun copyDatabaseIfNeeded() {
        val dbFile: File = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            Log.d(
                LOG_TAG, "Database not found, copying from assets..."
            )
            dbFile.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
            try {
                context.assets.open(DB_NAME)
                    .use { input -> FileOutputStream(dbFile).use { output -> input.copyTo(output) } }
                Log.d(LOG_TAG, "Database copied successfully.")
            } catch (e: IOException) {
                Log.e(LOG_TAG, "FAILED to copy database: ${e.message}")
                throw e
            }
        }
    }

    suspend fun getOrInsertBook(
        file: File, path: String, document: Document
    ): Book? {
        val bookId = checkAddBookToDatabase(file, path, document)
        val db = writableDatabase


        return db.query(
            "books", arrayOf(
                "id",
                "sha",
                "name",
                "location",
                "author",
                "isbn",
                "scanned",
                "last_opened",
                "toc_created",
                "toc_unavailable",
                "alternate_cover",
                "volume_title"
            ), "id = ?", arrayOf(bookId.toString()), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val (mainCategoryId, subCategoryIds) = getCategoryAssignments(id)
                Book(
                    id = id,
                    sha = cursor.getString(cursor.getColumnIndexOrThrow("sha")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    isbn = cursor.getString(cursor.getColumnIndexOrThrow("isbn")),
                    scanned = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("scanned")) == 1,
                    lastOpened = cursor.getLongOrNull(cursor.getColumnIndexOrThrow("last_opened")),
                    tocCreated = cursor.getLongOrNull(cursor.getColumnIndexOrThrow("toc_created")),
                    tocUnavailable = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("toc_unavailable")),
                    category = mainCategoryId,
                    subCategories = subCategoryIds,
                    alternateCover = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("alternate_cover")) == 1,
                    volumeTitle = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("volume_title")) == 1
                )
            } else null
        }
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
        val db = writableDatabase
        val values = ContentValues().apply {
            put(column, value)
        }
        val rowsUpdated = db.update("books", values, "id = ?", arrayOf(bookId.toString()))
        return rowsUpdated == 1
    }

    fun hasTableOfContents(bookId: Long): Boolean {
        val db = writableDatabase
        return db.query(
            "books", arrayOf("toc_created"), "id = ?", arrayOf(bookId.toString()), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0) == 1
            } else {
                false
            }
        }
    }

    private fun resolveOrCreateCategoryId(db: SQLiteDatabase, value: String): Int {
        db.rawQuery("SELECT id FROM categories WHERE category=?", arrayOf(value)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        val values = ContentValues().apply { put("category", value) }
        return db.insert("categories", null, values).toInt()
    }

    fun getKeywordCategories(keywords: String?): Pair<Int?, List<Int>> {

        val db = writableDatabase
        var mainCategoryId: Int? = null
        val subCategoryIds = mutableListOf<Int>()

        if (!keywords.isNullOrBlank()) {
            val pairs = keywords.split(",")
            for (pair in pairs) {
                val keyValue = pair.split("=").map { it.trim() }
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = keyValue[1]

                    if (key == "main_category") {
                        mainCategoryId = resolveOrCreateCategoryId(db, value)
                    } else if (key.contains("category", ignoreCase = true)) {
                        subCategoryIds.add(resolveOrCreateCategoryId(db, value))
                    }
                }
            }
        }

        return Pair(mainCategoryId, subCategoryIds)
    }


    suspend fun checkAddBookToDatabase(
        file: File, path: String, document: Document
    ): Long {
        val db = writableDatabase

        return db.query("books", arrayOf("id"), "location = ?", arrayOf(path), null, null, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val bookId = cursor.getLong(0)

                    val values = ContentValues().apply {
                        put("last_opened", System.currentTimeMillis())
                    }
                    db.update("books", values, "id = ?", arrayOf(bookId.toString()))
                    bookId
                } else {

                    val sha = file.sha256()
                    var bookAuthor: String? = null
                    var bookTitle: String? = null
                    val values = ContentValues().apply {
                        bookTitle = document.getMetaData(Document.META_INFO_TITLE)
                        bookAuthor = document.getMetaData(Document.META_INFO_AUTHOR)
                        put("name", bookTitle ?: file.name)
                        put("location", path)
                        put("sha", sha)
                        put("author", bookAuthor ?: "Unknown")
                        put("last_opened", System.currentTimeMillis())
                        put("toc_created", 0)
                    }

                    val keywords = document.getMetaData(Document.META_INFO_KEYWORDS)
                    val (mainId, subIds) = getKeywordCategories(keywords)

                    val newId = db.insert("books", null, values)

                    if (newId == -1L) {
                        Log.e(
                            LOG_TAG, "Failed to insert book! Check for column name mismatches."
                        )
                    } else {
                        if (mainId != null) setMainCategory(newId, mainId.toLong())
                        subIds.forEach { addSubCategory(newId, it.toLong()) }
                        generateBookCoverThumbnail(sha, document, bookTitle, bookAuthor)
                    }
                    newId
                }
            }
    }

    fun getFilteredEveryToc(currentText: String, currentCategory: String): MutableList<TocItem> {
        val db = readableDatabase
        val selectionArgs = if (currentCategory == "All") {
            arrayOf("%$currentText%", "%$currentText%")
        } else {
            arrayOf("%$currentText%", "%$currentText%", currentCategory)
        }
        val categoryFilter = if (currentCategory == "All") "" else "AND c.category = ?"
        val list = mutableListOf<TocItem>()

        val sql = """
WITH RECURSIVE toc_hierarchy AS (
    SELECT 
        id, 
        parent_id, 
        title,
        CAST('' AS TEXT) AS parent_path
    FROM toc
    WHERE parent_id IS NULL OR parent_id = 0
    
    UNION ALL
    
    SELECT 
        t.id, 
        t.parent_id, 
        t.title,
        CASE 
            WHEN th.parent_path = '' THEN th.title 
            ELSE th.parent_path || ' > ' || th.title 
        END
    FROM toc t
    JOIN toc_hierarchy th ON t.parent_id = th.id
)
SELECT 
    b.name AS book_name,
    b.id AS book_id,
    b.location AS book_location,
    t.id AS toc_id,
    t.parent_id AS parent_id,
    t.title AS toc_title,
    t.normalised_title AS normalised_title,
    t.bookmark_id AS toc_bookmark_id,
    h.parent_path AS breadcrumbs, 
    t.page AS toc_page,
    t.level AS toc_level,
    t.scale AS toc_scale,
    t.translate AS toc_translate
FROM books AS b
LEFT JOIN toc AS t ON b.id = t.book_id_fk
LEFT JOIN toc_hierarchy h ON t.id = h.id
LEFT JOIN book_category_map AS m ON m.book_id_fk = b.id
LEFT JOIN categories AS c ON c.id = m.category_id_fk
WHERE (t.title LIKE ? OR t.normalised_title LIKE ?)
$categoryFilter
GROUP BY t.id
ORDER BY b.name COLLATE NOCASE, CAST(t.page AS INTEGER)
""".trimIndent()

        val cursor = db.rawQuery(sql, selectionArgs)

        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val titleIndex = cursor.getColumnIndexOrThrow("toc_title")
                if (cursor.isNull(titleIndex)) continue
                val rawTitle = cursor.getString(titleIndex)
                val normalisedTitleIndex = cursor.getColumnIndexOrThrow("normalised_title")
                val normalisedTitle = cursor.getString(normalisedTitleIndex)
                val bookIdIndex = cursor.getColumnIndexOrThrow("book_id")
                val bookId = cursor.getLongOrNull(bookIdIndex)

                val breadcrumbs = cursor.getString(cursor.getColumnIndexOrThrow("breadcrumbs"))
                val hierarchyField = if (breadcrumbs.isNullOrBlank()) null else breadcrumbs

                val bookTitle = cursor.getString(cursor.getColumnIndexOrThrow("book_name"))
                val bookLocation = cursor.getString(cursor.getColumnIndexOrThrow("book_location"))
                val tocId = cursor.getLong(cursor.getColumnIndexOrThrow("toc_id"))
                val page = cursor.getInt(cursor.getColumnIndexOrThrow("toc_page"))
                val level = cursor.getInt(cursor.getColumnIndexOrThrow("toc_level"))

                val parentId = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("parent_id"))

                val scale =
                    if (cursor.isNull(cursor.getColumnIndexOrThrow("toc_scale"))) 1f else cursor.getFloat(
                        cursor.getColumnIndexOrThrow("toc_scale")
                    )

                val translate =
                    if (cursor.isNull(cursor.getColumnIndexOrThrow("toc_translate"))) 0f else cursor.getFloat(
                        cursor.getColumnIndexOrThrow("toc_translate")
                    )
                val bookmarkId = cursor.getIntOrNull(
                    cursor.getColumnIndexOrThrow("toc_bookmark_id")
                )
                list.add(
                    TocItem(
                        tocId = tocId,
                        bookId = bookId,
                        bookTitle = bookTitle,
                        bookLocation = bookLocation,
                        parentId = parentId,
                        title = rawTitle,
                        normalisedTitle = normalisedTitle,
                        hierarchy = hierarchyField,
                        page = page - 1,
                        level = level,
                        bookmarkId = bookmarkId,
                        scale = scale,
                        translate = translate
                    )
                )
            }
        }
        return list
    }


    suspend fun getCoverUrl(title: String, author: String): String? {
        return GetCoverUrlHelper().fetchRemoteCoverUrls(title, author).firstOrNull()
    }

    private suspend fun downloadAndSaveCover(
        sha: String,
        title: String,
        author: String,
        thumbnailFile: File,
        targetWidth: Int,
        targetHeight: Int
    ): Boolean {
        return try {
            val coverUrl = getCoverUrl(title, author) ?: return false

            val client = OkHttpClient()
            val request = Request.Builder().url(coverUrl).build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val originalBitmap = BitmapFactory.decodeStream(response.body.byteStream())
            val resultBitmap = createBitmap(targetWidth, targetHeight)
            val canvas = Canvas(resultBitmap)

            val matrix: GraphicsMatrix = GraphicsMatrix().apply {
                postScale(
                    targetWidth / originalBitmap.width.toFloat(),
                    targetHeight / originalBitmap.height.toFloat()
                )
            }
            canvas.drawBitmap(originalBitmap, matrix, null)

            withContext(Dispatchers.IO) {
                FileOutputStream(thumbnailFile).use { out ->
                    resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            updateIsAlternateCover(sha, 1)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun renderFirstPageBitmap(
        document: Document, targetWidth: Int, targetHeight: Int
    ): Bitmap {
        val page = document.loadPage(0)
        val bounds = page.bounds
        val pageWidth = bounds.x1 - bounds.x0
        val pageHeight = bounds.y1 - bounds.y0

        val scaleX = targetWidth / pageWidth
        val scaleY = targetHeight / pageHeight

        val bitmap = createBitmap(200, 300)

        val device = AndroidDrawDevice(bitmap, 0, 0)
        page.run(device, Matrix(scaleX, scaleY), null)

        device.close()
        device.destroy()
        page.destroy()

        return bitmap
    }

    private fun generateBookCoverFromFirstPage(
        sha: String, thumbnailFile: File, document: Document, targetWidth: Int, targetHeight: Int
    ): Boolean {
        return try {
            val bitmap = renderFirstPageBitmap(document, targetWidth, targetHeight)

            FileOutputStream(thumbnailFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            updateIsAlternateCover(sha, 0)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun renderFirstPageCoverPreview(
        document: Document, targetWidth: Int = 200, targetHeight: Int = 300
    ): Bitmap? {
        return try {
            renderFirstPageBitmap(document, targetWidth, targetHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
        return try {
            val hashName = "${sha}.png"
            val thumbnailFile = File(this.context.filesDir, hashName)
            if (thumbnailFile.exists() && !overwrite) return true

            val firstPage = FunctionalStructuredTextWalker().getPageCoordinates(document, 0)
            val generated = if (firstPage.imageIsFullPage) {
                generateBookCoverFromFirstPage(
                    sha, thumbnailFile, document, targetWidth, targetHeight
                )
            } else if (bookTitle != null && bookAuthor != null) {
                downloadAndSaveCover(
                    sha, bookTitle, bookAuthor, thumbnailFile, targetWidth, targetHeight
                )
            } else {
                false
            }
            generated

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

    fun getAllBookShas(): Set<String> {
        val db = readableDatabase
        val shas = mutableSetOf<String>()
        db.query("books", arrayOf("sha"), null, null, null, null, null).use { cursor ->
            val index = cursor.getColumnIndexOrThrow("sha")
            while (cursor.moveToNext()) {
                cursor.getString(index)?.let { shas.add(it) }
            }
        }
        return shas
    }

    fun updateIsAlternateCover(sha: String, isAlternateCover: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("alternate_cover", isAlternateCover)
        }
        db.update("books", values, "sha = ?", arrayOf(sha))
    }

    fun loadCategories(): List<Category> {
        val list = mutableListOf<Category>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, category FROM categories ORDER BY category", null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Category(
                        id = it.getLong(0), category = it.getString(1)
                    )
                )
            }
        }
        return list
    }

    fun createCategory(category: String): Long {
        val db = writableDatabase
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

    fun setMainCategory(bookId: Long, categoryId: Long) {
        val db = writableDatabase
        db.delete(
            "book_category_map",
            "book_id_fk = ? AND is_main = 1",
            arrayOf(bookId.toString())
        )
        db.delete(
            "book_category_map",
            "book_id_fk = ? AND category_id_fk = ?",
            arrayOf(bookId.toString(), categoryId.toString())
        )
        val values = ContentValues().apply {
            put("book_id_fk", bookId)
            put("category_id_fk", categoryId)
            put("is_main", 1)
        }
        db.insert("book_category_map", null, values)
    }

    fun addSubCategory(bookId: Long, categoryId: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("book_id_fk", bookId)
            put("category_id_fk", categoryId)
            put("is_main", 0)
        }
        db.insertWithOnConflict(
            "book_category_map", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun removeSubCategory(bookId: Long, categoryId: Long) {
        val db = writableDatabase
        db.delete(
            "book_category_map",
            "book_id_fk = ? AND category_id_fk = ? AND is_main = 0",
            arrayOf(bookId.toString(), categoryId.toString())
        )
        deleteCategoryIfUnused(db, categoryId)
    }

    private fun deleteCategoryIfUnused(db: SQLiteDatabase, categoryId: Long) {
        val stillUsed = db.rawQuery(
            "SELECT 1 FROM book_category_map WHERE category_id_fk = ? LIMIT 1",
            arrayOf(categoryId.toString())
        ).use { it.moveToFirst() }
        if (!stillUsed) {
            db.delete("categories", "id = ?", arrayOf(categoryId.toString()))
        }
    }

    fun getSubCategories(bookId: Long): List<Category> {
        val db = readableDatabase
        val list = mutableListOf<Category>()
        db.rawQuery(
            """
            SELECT c.id, c.category
            FROM book_category_map m
            JOIN categories c ON c.id = m.category_id_fk
            WHERE m.book_id_fk = ? AND m.is_main = 0
            ORDER BY c.category
            """.trimIndent(), arrayOf(bookId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Category(id = cursor.getLong(0), category = cursor.getString(1)))
            }
        }
        return list
    }

    fun getMainCategory(bookId: Long): Category? {
        val db = readableDatabase
        return db.rawQuery(
            """
            SELECT c.id, c.category
            FROM book_category_map m
            JOIN categories c ON c.id = m.category_id_fk
            WHERE m.book_id_fk = ? AND m.is_main = 1
            """.trimIndent(), arrayOf(bookId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Category(id = cursor.getLong(0), category = cursor.getString(1))
            } else null
        }
    }

    private fun getCategoryAssignments(bookId: Long): Pair<Int?, List<Int>> {
        val db = readableDatabase
        var mainCategoryId: Int? = null
        val subCategoryIds = mutableListOf<Int>()
        db.rawQuery(
            "SELECT category_id_fk, is_main FROM book_category_map WHERE book_id_fk = ?",
            arrayOf(bookId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val categoryId = cursor.getInt(0)
                if (cursor.getInt(1) == 1) {
                    mainCategoryId = categoryId
                } else {
                    subCategoryIds.add(categoryId)
                }
            }
        }
        return Pair(mainCategoryId, subCategoryIds)
    }

    fun getRecentFiles(): List<RecentFile> {
        val db = readableDatabase
        val list = mutableListOf<RecentFile>()
        val cursor = db.rawQuery(
            "SELECT location FROM books ORDER BY (last_opened IS NULL) ASC, last_opened DESC", null
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    RecentFile(
                        location = it.getString(0), lastOpened = it.getLongOrNull(1)
                    )
                )
            }
        }
        return list
    }

    fun getCategoriesForBookmarks(): List<CategoryItem> {
        val db = readableDatabase
        val list = mutableListOf<CategoryItem>()
        list.add(CategoryItem("All", null))
        val sql = """
            SELECT DISTINCT c.category AS used_categories
            FROM books AS b
            JOIN book_category_map AS m
              ON m.book_id_fk = b.id
            JOIN categories AS c
              ON c.id = m.category_id_fk
            JOIN bookmarks AS bm
              ON bm.book_id_fk = b.id
            WHERE c.category IS NOT NULL
            ORDER BY used_categories
        """.trimIndent()
        val cursor = db.rawQuery(
            sql, null
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = CategoryItem(cursor.getString(0), null)
                list.add(item)
            }
        }
        return list
    }

    fun getCategoriesForToc(currentSearchTerm: String): List<CategoryItem> {
        val db = readableDatabase
        val list = mutableListOf<CategoryItem>()
        list.add(CategoryItem("All", null))
        val selectionArgs = arrayOf(
            "%$currentSearchTerm%",
            "%$currentSearchTerm%"
        )
        val sql = """
            SELECT
                c.category AS used_category,
                COUNT(toc.id) AS toc_count
            FROM books b
            JOIN book_category_map m
                ON m.book_id_fk = b.id
            JOIN categories c
                ON c.id = m.category_id_fk
            LEFT JOIN toc
                ON toc.book_id_fk = b.id
               AND (
                    toc.title LIKE  ?
                    OR toc.normalised_title LIKE  ?
               )
            WHERE c.category IS NOT NULL
            GROUP BY c.category
            ORDER BY used_category;
        """.trimIndent()
        val cursor = db.rawQuery(
            sql, selectionArgs
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = CategoryItem(cursor.getString(0), cursor.getInt(1))
                list.add(item)
            }
        }
        return list
    }

    fun getUsedCategories(activityName: String?, currentSearchTerm: String): List<CategoryItem> {
        if (activityName == "BookmarksActivity") {
            return getCategoriesForBookmarks()
        }
        if (activityName == "EveryTocActivity" && currentSearchTerm != "") {
            return getCategoriesForToc(currentSearchTerm)
        }
        val db = readableDatabase
        val list = mutableListOf<CategoryItem>()
        list.add(CategoryItem("All", null))
        val sql = """
            SELECT DISTINCT c.category AS used_categories
            FROM books AS b
            JOIN book_category_map AS m
              ON m.book_id_fk = b.id
            JOIN categories AS c
              ON c.id = m.category_id_fk
            WHERE c.category IS NOT NULL
            ORDER BY used_categories
        """.trimIndent()
        val cursor = db.rawQuery(
            sql, null
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val item = CategoryItem(cursor.getString(0), null)
                list.add(item)
            }
        }
        return list
    }

    fun getMainCategoryNames(): List<String> {
        val db = readableDatabase
        val list = mutableListOf<String>()
        val sql = """
            SELECT DISTINCT c.category AS main_category
            FROM book_category_map AS m
            JOIN categories AS c
              ON c.id = m.category_id_fk
            WHERE m.is_main = 1
        """.trimIndent()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
        }
        return list
    }

    fun getBookInfoForItemPath(location: String): BookInfo? {
        val db = readableDatabase
        return db.query(
            "books", arrayOf("sha", "name"), "location = ?", arrayOf(location), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val sha = cursor.getString(cursor.getColumnIndexOrThrow("sha"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                BookInfo(sha, name)
            } else {
                null
            }
        }
    }

    private val bookWithCategoriesSelect = """
        SELECT b.sha AS book_sha, b.name AS book_name, c.category AS main_category,
               sub.sub_categories AS sub_categories,
               b.location AS book_location, b.author AS author_name
        FROM books AS b
        LEFT JOIN book_category_map AS mc ON mc.book_id_fk = b.id AND mc.is_main = 1
        LEFT JOIN categories AS c ON c.id = mc.category_id_fk
        LEFT JOIN (
            SELECT book_id_fk, GROUP_CONCAT(category, '||') AS sub_categories
            FROM (
                SELECT ms.book_id_fk AS book_id_fk, sc.category AS category
                FROM book_category_map AS ms
                JOIN categories AS sc ON sc.id = ms.category_id_fk
                WHERE ms.is_main = 0
                ORDER BY ms.id
            )
            GROUP BY book_id_fk
        ) AS sub ON sub.book_id_fk = b.id
    """.trimIndent()

    private fun readBookFileItems(cursor: android.database.Cursor): List<FileItem> {
        val list = mutableListOf<FileItem>()
        while (cursor.moveToNext()) {
            val sha = cursor.getString(cursor.getColumnIndexOrThrow("book_sha"))
            val name = cursor.getString(cursor.getColumnIndexOrThrow("book_name"))
            val mainCategory = cursor.getString(cursor.getColumnIndexOrThrow("main_category"))
            val subCategories = cursor.getString(cursor.getColumnIndexOrThrow("sub_categories"))
                ?.split("||")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val location = cursor.getString(cursor.getColumnIndexOrThrow("book_location"))
            val author = cursor.getString(cursor.getColumnIndexOrThrow("author_name"))
            val file = File(location)
            val bookInfo = BookInfo(sha, name, mainCategory, subCategories, author)
            list.add(FileItem(file, file.name, bookInfo, System.currentTimeMillis()))
        }
        return list
    }

    fun getBookShelfBooks(currentCategory: String): List<FileItem> {
        val db = readableDatabase

        val selectionArgs = if (currentCategory == "All") {
            null
        } else {
            arrayOf(currentCategory)
        }
        val categoryFilter = if (currentCategory == "All") "" else """
            WHERE EXISTS (
                SELECT 1 FROM book_category_map fm
                JOIN categories fc ON fc.id = fm.category_id_fk
                WHERE fm.book_id_fk = b.id AND fc.category = ?
            )
        """.trimIndent()
        val sql = """
            $bookWithCategoriesSelect
            $categoryFilter
            ORDER BY (mc.category_id_fk IS NULL) ASC, mc.category_id_fk ASC, b.author;
        """.trimIndent()
        return db.rawQuery(sql, selectionArgs).use { cursor -> readBookFileItems(cursor) }
    }

    fun getDistinctBookNames(term: String): List<String> {
        val db = readableDatabase
        val list = mutableListOf<String>()
        db.rawQuery(
            "SELECT DISTINCT name FROM books WHERE name LIKE ? ORDER BY name COLLATE NOCASE LIMIT 20",
            arrayOf("%$term%")
        ).use { cursor ->
            while (cursor.moveToNext()) list.add(cursor.getString(0))
        }
        return list
    }

    fun getDistinctBookAuthors(term: String): List<String> {
        val db = readableDatabase
        val list = mutableListOf<String>()
        db.rawQuery(
            "SELECT DISTINCT author FROM books WHERE author IS NOT NULL AND author LIKE ? ORDER BY author COLLATE NOCASE LIMIT 20",
            arrayOf("%$term%")
        ).use { cursor ->
            while (cursor.moveToNext()) list.add(cursor.getString(0))
        }
        return list
    }

    fun getBooksByExactName(name: String): List<FileItem> {
        val db = readableDatabase
        val sql = """
            $bookWithCategoriesSelect
            WHERE b.name = ?
            ORDER BY b.name COLLATE NOCASE;
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(name)).use { cursor -> readBookFileItems(cursor) }
    }

    fun getBooksByExactAuthor(author: String): List<FileItem> {
        val db = readableDatabase
        val sql = """
            $bookWithCategoriesSelect
            WHERE b.author = ?
               OR b.author LIKE ? || ' & %'
               OR b.author LIKE '% & ' || ?
               OR b.author LIKE '% & ' || ? || ' & %'
            ORDER BY b.name COLLATE NOCASE;
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(author, author, author, author)).use { cursor -> readBookFileItems(cursor) }
    }

    fun generateToc(
        document: Document, bookId: Long, progressCallback: ((percent: Int) -> Unit)? = null
    ): Boolean {
        try {
            val outline = document.loadOutline() ?: return false
            val db = writableDatabase

            var ignoredOffset: Float? = null
            var ignoredScale: Float? = null
            var ignoredTranslate: Float? = null

            val flatList = flattenOutline(outline)
            val total = flatList.size
            var processed = 0

            fun insertWithProgress(
                entries: List<Outline>, parentId: Long?, level: Int
            ) {
                val stmt = db.compileStatement(
                    """
                INSERT INTO toc (book_id_fk, parent_id, level, title, normalised_title, page, scale, translate)
                VALUES (?,?,?,?,?,?,?,?)
                """.trimIndent()
                )

                entries.forEach { entry ->
                    val page = extractPageFromUri(entry.uri)
                    val pageCoordinates = FunctionalStructuredTextWalker().getPageCoordinates(
                        document, page - 1
                    )

                    ignoredOffset = ignoredOffset?.let { minOf(it, pageCoordinates.leftOffset) }
                        ?: pageCoordinates.leftOffset
                    ignoredScale = ignoredScale?.let { minOf(it, pageCoordinates.targetScale) }
                        ?: pageCoordinates.targetScale
                    ignoredTranslate = ignoredTranslate?.let {
                        minOf(
                            it, pageCoordinates.translatingPercentage
                        )
                    } ?: pageCoordinates.translatingPercentage

                    stmt.clearBindings()
                    stmt.bindLong(1, bookId)
                    parentId?.let { stmt.bindLong(2, it) } ?: stmt.bindNull(2)
                    stmt.bindLong(3, level.toLong())
                    val title = entry.title ?: ""
                    stmt.bindString(4, title)
                    stmt.bindString(5, normalizeText(title))
                    stmt.bindLong(6, page.toLong())
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
            db.update("books", values, "id = ?", arrayOf(bookId.toString()))
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun flattenOutline(outline: Array<Outline>): List<Outline> {
        val result = mutableListOf<Outline>()
        fun recurse(entries: Array<Outline>) {
            entries.forEach {
                result.add(it)
                if (!it.down.isNullOrEmpty()) recurse(it.down)
            }
        }
        recurse(outline)
        return result
    }

    fun getBook(location: String): Book? {
        val db = readableDatabase
        return db.query(
            "books", arrayOf(
                "id",
                "sha",
                "name",
                "location",
                "author",
                "isbn",
                "scanned",
                "last_opened",
                "toc_created",
                "toc_unavailable",
                "alternate_cover",
                "volume_title"
            ), "location = ?", arrayOf(location), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val (mainCategoryId, subCategoryIds) = getCategoryAssignments(id)
                Book(
                    id = id,
                    sha = cursor.getString(cursor.getColumnIndexOrThrow("sha")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    isbn = cursor.getString(cursor.getColumnIndexOrThrow("isbn")),
                    scanned = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("scanned")) == 1,
                    lastOpened = cursor.getLongOrNull(cursor.getColumnIndexOrThrow("last_opened")),
                    tocCreated = cursor.getLongOrNull(cursor.getColumnIndexOrThrow("toc_created")),
                    tocUnavailable = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("toc_unavailable")),
                    category = mainCategoryId,
                    subCategories = subCategoryIds,
                    alternateCover = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("alternate_cover")) == 1,
                    volumeTitle = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("volume_title")) == 1
                )
            } else null
        }
    }

    fun updateBookIsbn(bookId: Long, foundIsbn: IsbnResult) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("isbn", foundIsbn.isbn)
            val scanned = if (foundIsbn.scanned) 1 else 0
            put("scanned", scanned)
        }
        db.update("books", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun currentQuery(currentText: String, currentCategory: String): Long {
        val db = readableDatabase
        val selectionArgs = if (currentCategory == "All") {
            arrayOf("%$currentText%", "%$currentText%")
        } else {
            arrayOf("%$currentText%", "%$currentText%", currentCategory)
        }
        val categoryFilter = if (currentCategory == "All") "" else "AND c.category = ?"

        val sql = """
        SELECT COUNT(*) FROM (
            WITH RECURSIVE toc_hierarchy AS (
                SELECT id, parent_id FROM toc WHERE parent_id IS NULL OR parent_id = 0
                UNION ALL
                SELECT t.id, t.parent_id FROM toc t JOIN toc_hierarchy th ON t.parent_id = th.id
            )
            SELECT t.id
            FROM books AS b
            LEFT JOIN toc AS t ON b.id = t.book_id_fk
            LEFT JOIN book_category_map AS m ON m.book_id_fk = b.id
            LEFT JOIN categories AS c ON c.id = m.category_id_fk
            WHERE (t.title LIKE ? OR t.normalised_title LIKE ?)
            $categoryFilter
            GROUP BY t.id 
        )
    """.trimIndent()

        return db.compileStatement(sql).run {
            bindAllArgsAsStrings(selectionArgs)
            simpleQueryForLong()
        }
    }

    fun getTocRows(bookId: Int): List<Row> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT 
                t.id AS toc_id,
                t.parent_id AS parent_id, 
                t.title AS toc_title, 
                t.normalised_title AS normalised_title, 
                t.page AS page_title, 
                t.level AS TOC_LEVEL, 
                t.bookmark_id AS bookmark_id,
                t.scale AS toc_scale, 
                t.translate AS toc_translate,
                b.name AS book_title,
                p.title AS parent_title
            FROM toc AS t
            LEFT JOIN books AS b
                ON t.book_id_fk = b.id
            LEFT JOIN toc AS p
                ON t.parent_id = p.id
            WHERE t.book_id_fk = ?
            ORDER BY t.id
        """.trimIndent(), arrayOf(bookId.toString())
        )


        val rows = mutableListOf<Row>()
        cursor.use {
            while (cursor.moveToNext()) {

                rows.add(
                    Row(
                        id = cursor.getLong(0),
                        bookId = bookId.toLong(),
                        bookTitle = cursor.getString(9),
                        parentId = cursor.getLongOrNull(1),
                        title = cursor.getString(2),
                        normalisedTitle = cursor.getString(3),
                        page = cursor.getInt(4) - 1,
                        level = cursor.getInt(5),
                        bookmarkId = cursor.getIntOrNull(6),
                        scale = cursor.getFloat(7),
                        translate = cursor.getFloat(8),
                        parentTitle = cursor.getStringOrNull(10)
                    )
                )
            }
        }
        return rows
    }

    fun createBookmark(item: BookmarkItem): Boolean {
        val db = writableDatabase
        val bookIdStr = item.bookId?.toString() ?: "NULL"
        val uniqueKey = "$bookIdStr|${item.title}|${item.page}|${item.offset}|${item.tocId}"
        val isImage = if (item.isImage) 1L else 0L

        val insertSql = """
        INSERT OR IGNORE INTO bookmarks 
        (book_id_fk, title, normalised_title, page, `offset`, scale, translate, unique_key, is_image)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

        try {
            db.compileStatement(insertSql).use { stmt ->
                // Bind parameters safely
                if (item.bookId != null) stmt.bindLong(1, item.bookId) else stmt.bindNull(1)
                stmt.bindString(2, item.title)
                stmt.bindString(3, item.normalisedTitle)
                stmt.bindLong(4, item.page.toLong())
                if (item.offset != null) stmt.bindLong(
                    5,
                    item.offset.toLong()
                ) else stmt.bindNull(5) // <-- fixed
                stmt.bindDouble(6, item.scale.toDouble())
                stmt.bindDouble(7, item.translate.toDouble())
                stmt.bindString(8, uniqueKey)
                stmt.bindLong(9, isImage)

                val rowId = stmt.executeInsert()
                if (rowId == -1L) {
                    Log.w(LOG_TAG, "Insert ignored due to unique constraint: uniqueKey=$uniqueKey")
                    return false
                } else {
                    // Update toc with new bookmark_id
                    val values = ContentValues().apply { put("bookmark_id", rowId) }
                    val updatedRows =
                        db.update("toc", values, "id = ?", arrayOf(item.tocId.toString()))
                    if (updatedRows == 0) {
                        Log.w(LOG_TAG, "TOC row not updated, tocId=${item.tocId}")
                    }
                    Log.d(LOG_TAG, "Bookmark inserted successfully, rowId=$rowId")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to insert bookmark: ${e.message}", e)
            return false
        }
    }

    fun updateBookmark(item: BookmarkItem, currentCategory: String): List<BookmarkItem> {
        val db = writableDatabase
        val isImageInt = if (item.isImage) 1 else 0
        val values = ContentValues().apply {
            put("title", item.title)
            put("is_image", isImageInt)
        }
        db.update("bookmarks", values, "id = ?", arrayOf(item.bookmarkId.toString()))
        return getAllBookmarks(currentCategory)
    }

    fun deleteBookmark(item: BookmarkItem): Boolean {
        val db = writableDatabase
        db.beginTransaction()

        return try {
            db.delete(
                "bookmarks", "id = ?", arrayOf(item.bookmarkId.toString())
            )

            db.execSQL(
                "UPDATE toc SET bookmark_id = NULL WHERE id = ?", arrayOf(item.tocId)
            )

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            db.endTransaction()
        }
    }

    fun getAllBookmarks(currentCategory: String): List<BookmarkItem> {
        val db = readableDatabase
        val selectionArgs = if (currentCategory == "All") {
            null
        } else {
            arrayOf(currentCategory)
        }
        val categoryFilter = if (currentCategory == "All") "" else "WHERE c.category = ?"
        val cursor = db.rawQuery(
            """
            SELECT 
                m.id AS bookmark_id,
                m.book_id_fk AS book_id, 
                m.title AS bookmark_title, 
                m.normalised_title AS normalised_title, 
                m.page AS bookmark_page, 
                m.`offset` AS bookmark_offset, 
                m.scale AS bookmark_scale, 
                m.translate AS bookmark_translate,
                b.name AS book_tite,
                b.location AS book_location,
                m.is_image AS bookmark_is_image
            FROM bookmarks AS m
            LEFT JOIN books AS  b
            ON m.book_id_fk = b.id
            LEFT JOIN book_category_map AS bcm ON bcm.book_id_fk = b.id
            LEFT JOIN categories AS c ON c.id = bcm.category_id_fk
            $categoryFilter
            GROUP BY m.id
            ORDER BY LOWER(b.name), m.page
        """.trimIndent(), selectionArgs
        )
        val bookmarks = mutableListOf<BookmarkItem>()
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                bookmarks.add(
                    BookmarkItem(
                        tocId = null,
                        bookmarkId = cursor.getLongOrNull(0),
                        title = cursor.getString(2),
                        normalisedTitle = cursor.getString(3),
                        bookTitle = cursor.getString(8),
                        bookId = cursor.getLongOrNull(1),
                        page = cursor.getInt(4),
                        offset = cursor.getIntOrNull(5),
                        scale = cursor.getFloat(6),
                        translate = cursor.getFloat(7),
                        bookLocation = cursor.getStringOrNull(9),
                        isImage = cursor.getIntOrNull(10) == 1,
                    )
                )
            }
        }
        return bookmarks
    }

    fun addBookHistoryItem(historyItem: BookHistoryItem): List<BookHistoryItem> {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("book_id_fk", historyItem.bookId)
            put("page", historyItem.page)
            put("offset", historyItem.offset)
            put("translation_x", historyItem.translationX)
            put("scale", historyItem.scale)
        }
        db.insert("history", null, values)
        return getBookHistory(historyItem.bookId!!)
    }

    fun updateBookHistoryItem(historyItem: BookHistoryItem): List<BookHistoryItem> {
        val db = writableDatabase

        val values = ContentValues().apply {
            put("page", historyItem.page)
            put("offset", historyItem.offset)
            put("translation_x", historyItem.translationX)
            put("scale", historyItem.scale)
        }
        db.update(
            "history", values, "id = ?", arrayOf(historyItem.id.toString())
        )
        return getBookHistory(historyItem.bookId!!)
    }

    fun removeBookHistoryItem(historyItemId: Long, bookId: Long): List<BookHistoryItem> {
        val db = writableDatabase
        db.delete("history", "id = ?", arrayOf(historyItemId.toString()))
        return getBookHistory(bookId)
    }

    fun getBookHistory(bookId: Long): List<BookHistoryItem> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT 
                h.id AS history_id,
                h.book_id_fk AS history_book_id,
                h.page AS history_page, 
                h.`offset` AS history_offset, 
                h.translation_x AS history_translation_x,
                h.scale AS history_scale
            FROM history AS h
            WHERE h.book_id_fk = ?
            ORDER BY h.id DESC ;
        """.trimIndent(), arrayOf(bookId.toString())
        )
        val history = mutableListOf<BookHistoryItem>()
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                history.add(
                    BookHistoryItem(
                        id = cursor.getLongOrNull(0),
                        bookId = cursor.getLongOrNull(1),
                        page = cursor.getInt(2),
                        offset = cursor.getIntOrNull(3),
                        translationX = cursor.getFloatOrNull(4),
                        scale = cursor.getFloatOrNull(5)
                    )
                )
            }
        }
        return history
    }

    fun getBookmarksForBook(bookId: Int): List<BookmarkItem> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT 
                m.id AS bookmark_id,
                m.book_id_fk AS book_id, 
                m.title AS bookmark_title,
                m.normalised_title AS normalised_title,
                m.page AS bookmark_page, 
                m.`offset` AS bookmark_offset, 
                m.scale AS bookmark_scale, 
                m.translate AS bookmark_translate,
                b.name AS book_tite,
                b.location AS book_location
            FROM bookmarks AS m
            LEFT JOIN books AS  b
            ON m.book_id_fk = b.id
            WHERE m.book_id_fk = ?
            ORDER BY m.page
        """.trimIndent(), arrayOf(bookId.toString())
        )
        val bookmarks = mutableListOf<BookmarkItem>()
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                bookmarks.add(
                    BookmarkItem(
                        tocId = null,
                        bookmarkId = cursor.getLongOrNull(0),
                        title = cursor.getString(2),
                        normalisedTitle = cursor.getStringOrNull(3) ?: "",
                        bookTitle = cursor.getString(8),
                        bookId = cursor.getLongOrNull(1),
                        page = cursor.getInt(4),
                        offset = cursor.getIntOrNull(5),
                        scale = cursor.getFloat(6),
                        translate = cursor.getFloat(7),
                        bookLocation = cursor.getStringOrNull(9),
                    )
                )
            }
        }
        return bookmarks
    }

    fun clearBookHistory(bookId: Long) {
        val db = writableDatabase
        db.delete(
            "history", "book_id_fk = ?", arrayOf(bookId.toString())
        )

    }

    fun getSubsequentTocItem(page: Int, lastTokId: Long?, up: Boolean, bookId: Long): TocItem? {
        val db = readableDatabase
        val comparison = if (up) "<" else ">"
        val boundColumn = if (lastTokId == null) "t.page" else "t.id"
        val whereClause = "WHERE t.book_id_fk = ? AND $boundColumn $comparison ?"
        val direction = if (up) "DESC" else "ASC"
        val selectionArgs = if (lastTokId == null) arrayOf(
            bookId.toString(),
            page.toString()
        ) else arrayOf(bookId.toString(), lastTokId.toString())

        return db.rawQuery(
            """
             SELECT 
                b.name AS book_name,
                b.location AS book_location,
                t.id AS toc_id,
                t.parent_id AS parent_id,
                t.title AS toc_title,
                t.normalised_title AS normalised_title,
                t.bookmark_id AS toc_bookmark_id,
                t.page AS toc_page,
                t.level AS toc_level,
                t.scale AS toc_scale,
                t.translate AS toc_translate
            FROM books AS b 
            LEFT JOIN toc AS t ON b.id = t.book_id_fk 
            $whereClause
            ORDER BY $boundColumn $direction
			LIMIT 1
        """.trimIndent(), selectionArgs
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                TocItem(
                    tocId = cursor.getLong(cursor.getColumnIndexOrThrow("toc_id")),
                    bookId = bookId,
                    bookTitle = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("book_name")),
                    parentId = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("parent_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("toc_title")),
                    normalisedTitle = cursor.getString(cursor.getColumnIndexOrThrow("normalised_title")),
                    page = cursor.getInt(cursor.getColumnIndexOrThrow("toc_page")) - 1,
                    offset = null,
                    level = cursor.getInt(cursor.getColumnIndexOrThrow("toc_level")),
                    scale = cursor.getFloat(cursor.getColumnIndexOrThrow("toc_scale")),
                    translate = cursor.getFloat(cursor.getColumnIndexOrThrow("toc_translate")),
                )
            } else null
        }
    }

    fun deleteBook(currentBookId: Long): Boolean {
        val db = writableDatabase
        return db.transaction {
            try {
                val bookIdString = currentBookId.toString()
                delete("toc", "book_id_fk = ?", arrayOf(bookIdString))
                delete("bookmarks", "book_id_fk = ?", arrayOf(bookIdString))
                delete("recent_recipes", "book_id_fk = ?", arrayOf(bookIdString))
                delete("history", "book_id_fk = ?", arrayOf(bookIdString))
                delete("books", "id = ?", arrayOf(bookIdString))
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun setTocUnavailable(bookId: Long): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("toc_unavailable", 1)
        }
        val rowsUpdated = db.update("books", values, "id = ?", arrayOf(bookId.toString()))
        return rowsUpdated == 1
    }

    fun getTocUnavailable(bookId: Long): Boolean {
        val db = readableDatabase
        return db.query(
            "books", arrayOf(
                "toc_unavailable",
            ), "id = ?", arrayOf(bookId.toString()), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getIntOrNull(cursor.getColumnIndexOrThrow("toc_unavailable")) == 1
            } else false
        }
    }

    fun updateVolumeTitleStatus(bookId: Long, checked: Boolean): Boolean {
        val db = writableDatabase
        val volumeTitle = if (checked) 1 else 0
        val values = ContentValues().apply {
            put("volume_title", volumeTitle)
        }
        val rowsUpdated = db.update("books", values, "id = ?", arrayOf(bookId.toString()))
        return rowsUpdated == 1
    }

    fun parseQuantity(qty: String): Double {
        val trimmed = qty.trim()

        val unicodeFractions = mapOf(
            '½' to 0.5,
            '⅓' to 1.0 / 3,
            '⅔' to 2.0 / 3,
            '¼' to 0.25,
            '¾' to 0.75,
            '⅕' to 0.2,
            '⅖' to 0.4,
            '⅗' to 0.6,
            '⅘' to 0.8,
            '⅙' to 1.0 / 6,
            '⅚' to 5.0 / 6,
            '⅛' to 0.125,
            '⅜' to 0.375,
            '⅝' to 0.625,
            '⅞' to 0.875
        )

        // Step 1: Replace Unicode fractions with space-separated decimal value
        var qtyStr = trimmed
        var total = 0.0

        // Handle mixed unicode fractions like "1½" or "2¾"
        val mixedUnicodeRegex = Regex("""(\d+)([½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞])""")
        mixedUnicodeRegex.findAll(qtyStr).forEach { m ->
            val base = m.groupValues[1].toDouble()
            val frac = unicodeFractions[m.groupValues[2][0]] ?: 0.0
            total += base + frac
            // remove matched part from string to avoid double-count
            qtyStr = qtyStr.replace(m.value, "")
        }

        // Step 2: Sum standalone unicode fractions
        val unicodeValue = qtyStr.mapNotNull { unicodeFractions[it] }.sum()
        total += unicodeValue

        // Remove any Unicode fraction characters from string
        qtyStr = qtyStr.replace(Regex("[½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞]"), "").trim()

        // Step 3: Handle remaining numbers/fractions
        if (qtyStr.contains(" ")) {
            val parts = qtyStr.split(" ")
            if (parts.size >= 2) {
                // Mixed fraction like "1 1/2"
                total += parts[0].toDoubleOrNull() ?: 0.0
                total += parseFraction(parts[1])
            } else {
                total += parts[0].toDoubleOrNull() ?: 0.0
            }
        } else if (qtyStr.contains("/")) {
            total += parseFraction(qtyStr)
        } else if (qtyStr.isNotEmpty()) {
            total += qtyStr.toDoubleOrNull() ?: 0.0
        }

        return total
    }

    private fun parseFraction(frac: String): Double {
        val parts = frac.split("/")
        return if (parts.size == 2) {
            val numerator = parts[0].toDoubleOrNull() ?: 0.0
            val denominator = parts[1].toDoubleOrNull() ?: 1.0
            numerator / denominator
        } else 0.0
    }

    fun parseInput(text: String?): Triple<Double, String, String>? {
        if (text.isNullOrBlank()) return null
        val cleanedText = text.replace(Regex("""\s*\(.*?\)"""), "").trim()
        val regex =
            Regex("""^\s*([\d\s./½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞]+(?:\s*cups?))\s+(.*)$""", RegexOption.IGNORE_CASE)

        val match = regex.find(cleanedText) ?: return null
        val (volumeStr, ingredient) = match.destructured
        val quantity = parseQuantity(volumeStr.trim())
        return Triple(quantity, volumeStr.trim(), ingredient.trim())
    }

    private fun convertGrams(gramsStr: String, originalCups: Float, targetCups: Double): String {
        return try {
            if ("to" in gramsStr) {
                // Range like "227 to 241"
                val parts = gramsStr.split("to").map { it.trim().toFloat() }
                if (parts.size == 2) {
                    val convertedLow = parts[0] * targetCups / originalCups
                    val convertedHigh = parts[1] * targetCups / originalCups
                    "%.1f to %.1f".format(convertedLow, convertedHigh)
                } else gramsStr
            } else {
                // Single number
                val value = gramsStr.toFloat()
                "%.1f".format(value * targetCups / originalCups)
            }
        } catch (e: Exception) {
            gramsStr // fallback if parsing fails
        }
    }

    @SuppressLint("DefaultLocale")
    fun getChartConversion(searchedIngredient: String?): String {
        if (searchedIngredient?.contains("°") == true) {
            val parts = searchedIngredient.split("°")
            val value = parts[0].toInt()
            val unit = parts[1]
            if (unit.lowercase() == "f") {
                val celsius = (value - 32) * 5 / 9 * 1F
                return "${celsius.toInt()}°C"
            } else if (unit.lowercase() == "c") {
                val fahrenheit = value * 9 / 5 + 32 * 1F
                return "${fahrenheit.toInt()}°F"
            }
        }
        val db = readableDatabase
        val result = StringBuilder()

        val parsedResult = parseInput(searchedIngredient)
        val third = parsedResult?.third
        var parsedIngredient = searchedIngredient

        if (third != null) {
            parsedIngredient = third
        }
        parsedIngredient = normalizeText(parsedIngredient ?: "")
        val first = parsedResult?.first
        val second = parsedResult?.second
        db.query(
            "weight_chart",
            arrayOf("ingredient", "volume", "grams", "decimal_cups"),
            """
                ingredient LIKE ? 
                OR ingredient LIKE ? 
                OR ingredient LIKE ? 
                OR ingredient LIKE ?
                """.trimIndent(),
            arrayOf(
                parsedIngredient,
                "$parsedIngredient %",   // starts with "butter "
                "% $parsedIngredient",   // ends with " butter"
                "% $parsedIngredient %"  // in middle "salted butter"
            ),
            null,
            null,
            null
        ).use { cursor ->

            val ingredientCol = cursor.getColumnIndexOrThrow("ingredient")
            val volumeCol = cursor.getColumnIndexOrThrow("volume")
            val gramsCol = cursor.getColumnIndexOrThrow("grams")
            val decimalCupsCol = cursor.getColumnIndexOrThrow("decimal_cups")

            while (cursor.moveToNext()) {
                val ingredient = cursor.getString(ingredientCol)
                val volume = cursor.getString(volumeCol)
                val grams = cursor.getString(gramsCol)
                val decimalCups = cursor.getFloatOrNull(decimalCupsCol)
                if (decimalCups != null && first != 0.0 && first != null && second != null) {
                    val convertedGrams = convertGrams(grams, decimalCups, first)
                    result.append("$ingredient - $second - ${convertedGrams.replace(".0", "")} g\n")
                } else {
                    result.append("$ingredient - $volume - $grams g\n")
                }
            }
        }

        return result.toString().trim()
    }

    fun normalizeText(title: String): String {

        // Remove diacritics
        var normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

        // Replace curly quotes with straight quotes
        normalized = normalized
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")

        return normalized
    }

    fun addRecentRecipeItem(item: BaseBookmarkTocItem): Boolean {
        deleteRecentRecipe(item)
        cleanupRecentRecipes()
        val db = writableDatabase
        val insertSql = """
        INSERT OR IGNORE INTO recent_recipes 
        (book_id_fk, title, normalised_title, page, `offset`, scale, translate, last_opened)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

        try {
            db.compileStatement(insertSql).use { stmt ->
                // Bind parameters safely
                if (item.bookId != null) stmt.bindLong(1, item.bookId!!) else stmt.bindNull(1)
                stmt.bindString(2, item.title)
                stmt.bindString(3, item.normalisedTitle)
                stmt.bindLong(4, item.page.toLong())
                if (item.offset != null) stmt.bindLong(
                    5,
                    item.offset!!.toLong()
                ) else stmt.bindNull(5) // <-- fixed
                stmt.bindDouble(6, item.scale.toDouble())
                stmt.bindDouble(7, item.translate.toDouble())
                stmt.bindLong(8, System.currentTimeMillis())
                val rowId = stmt.executeInsert()
                if (rowId == -1L) {

                    return false
                } else {
                    Log.d(LOG_TAG, "Recent recipe inserted successfully, rowId=$rowId")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to insert Recent recipe: ${e.message}", e)
            return false
        }
    }


    fun getAllRecentRecipes(): List<RecentRecipeItem> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT 
                m.id AS recent_recipe_id,
                m.book_id_fk AS book_id, 
                m.title AS recent_recipe_title, 
                m.normalised_title AS normalised_title, 
                m.page AS recent_recipe_page, 
                m.`offset` AS recent_recipe_offset, 
                m.scale AS recent_recipe_scale, 
                m.translate AS recent_recipe_translate,
                b.name AS book_tite,
                b.location AS book_location,
                m.is_image AS recent_recipe_is_image,
                m.last_opened AS recent_recipe_last_opened
            FROM recent_recipes AS m
            LEFT JOIN books AS  b
            ON m.book_id_fk = b.id
            GROUP BY m.book_id_fk, m.title, m.page
            ORDER BY m.last_opened DESC
        """.trimIndent(), null
        )
        val recentRecipes = mutableListOf<RecentRecipeItem>()
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                recentRecipes.add(
                    RecentRecipeItem(
                        tocId = null,
                        recentRecipeId = cursor.getLongOrNull(0),
                        title = cursor.getString(2),
                        lastOpened = cursor.getLongOrNull(11),
                        normalisedTitle = cursor.getString(3),
                        bookTitle = cursor.getString(8),
                        bookId = cursor.getLongOrNull(1),
                        page = cursor.getInt(4),
                        offset = cursor.getIntOrNull(5),
                        scale = cursor.getFloat(6),
                        translate = cursor.getFloat(7),
                        bookLocation = cursor.getStringOrNull(9),
                        isImage = cursor.getIntOrNull(10) == 1,
                    )
                )
            }
        }
        return recentRecipes
    }

    fun deleteRecentRecipe(item: BaseBookmarkTocItem): Boolean {
        val db = writableDatabase
        db.beginTransaction()

        return try {
            db.delete(
                "recent_recipes",
                "book_id_fk = ? AND title = ? AND page = ?",
                arrayOf(item.bookId.toString(), item.title, item.page.toString())
            )

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            db.endTransaction()
        }
    }

    fun cleanupRecentRecipes() {
        writableDatabase.execSQL("""
        DELETE FROM recent_recipes
        WHERE id NOT IN (
            SELECT id
            FROM recent_recipes
            ORDER BY last_opened DESC
            LIMIT 20
        )
    """.trimIndent())
    }

}
