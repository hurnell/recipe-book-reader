package apk.hurnell.recipebookreader.helpers

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
import apk.hurnell.recipebookreader.model.BookHistoryItem
import apk.hurnell.recipebookreader.model.BookmarkItem
import apk.hurnell.recipebookreader.model.Row
import apk.hurnell.recipebookreader.model.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request


class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 1) {

    companion object {
        private const val DB_NAME = "recipe-reader.db"
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Not used because we copy prebuilt DB from assets
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle migrations later if needed
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
                "category",
                "sub_category",
                "alternate_cover"
            ), "id = ?", arrayOf(bookId.toString()), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Book(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    sha = cursor.getString(cursor.getColumnIndexOrThrow("sha")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    isbn = cursor.getString(cursor.getColumnIndexOrThrow("isbn")),
                    scanned = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("scanned")) == 1,
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

    fun getKeywordCategories(keywords: String?): Pair<Int?, Int?> {

        val db = writableDatabase
        var mainCategoryId: Int? = null
        var subCategoryId: Int? = null

        if (!keywords.isNullOrBlank()) {
            val pairs = keywords.split(",")
            for (pair in pairs) {
                val keyValue = pair.split("=").map { it.trim() }
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = keyValue[1]
                    val query = "SELECT id FROM categories WHERE category=?"

                    when (key) {
                        "main_category" -> {
                            db.rawQuery(query, arrayOf(value)).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    mainCategoryId = cursor.getInt(0)
                                }
                            }
                        }

                        "sub_category" -> {
                            db.rawQuery(query, arrayOf(value)).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    subCategoryId = cursor.getInt(0)
                                }
                            }
                        }
                    }
                }
            }
        }

        return Pair(mainCategoryId, subCategoryId)
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
                        val keywords = document.getMetaData(Document.META_INFO_KEYWORDS)

                        val (mainId, subId) = getKeywordCategories(keywords)

                        put("name", bookTitle ?: file.name)
                        put("category", mainId)
                        put("sub_category", subId)
                        put("location", path)
                        put("sha", sha)
                        put("author", bookAuthor ?: "Unknown")
                        put("last_opened", System.currentTimeMillis())
                        put("toc_created", 0)
                    }

                    val newId = db.insert("books", null, values)

                    if (newId == -1L) {
                        Log.e(
                            LOG_TAG, "Failed to insert book! Check for column name mismatches."
                        )
                    } else {
                        generateBookCoverThumbnail(sha, document, bookTitle, bookAuthor)
                    }
                    newId
                }
            }
    }

    fun getFilteredEveryToc(currentText: String, currentCategory: String): MutableList<TocItem> {
        val db = readableDatabase
        val selectionArgs = if (currentCategory == "All") {
            arrayOf("%$currentText%")
        } else {
            arrayOf("%$currentText%", currentCategory)
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
    t.bookmark_id AS toc_bookmark_id,
    h.parent_path AS breadcrumbs, 
    t.page AS toc_page,
    t.level AS toc_level,
    t.scale AS toc_scale,
    t.translate AS toc_translate
FROM books AS b 
LEFT JOIN toc AS t ON b.id = t.book_id_fk 
LEFT JOIN toc_hierarchy h ON t.id = h.id
LEFT JOIN categories AS c ON b.category = c.id OR b.sub_category = c.id
WHERE t.title LIKE ? 
$categoryFilter
GROUP BY t.id
ORDER BY b.name COLLATE NOCASE, t.page
""".trimIndent()

        val cursor = db.rawQuery(sql, selectionArgs)

        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val titleIndex = cursor.getColumnIndexOrThrow("toc_title")
                if (cursor.isNull(titleIndex)) continue
                val rawTitle = cursor.getString(titleIndex)
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
        title: String, author: String, thumbnailFile: File, targetWidth: Int, targetHeight: Int
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


            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generateBookCoverFromFirstPage(
        thumbnailFile: File, document: Document, targetWidth: Int, targetHeight: Int
    ): Boolean {
        return try {
            val page = document.loadPage(0)
            val bounds = page.bounds
            val pageWidth = bounds.x1 - bounds.x0
            val pageHeight = bounds.y1 - bounds.y0

            val scaleX = targetWidth / pageWidth
            val scaleY = targetHeight / pageHeight

            val bitmap = createBitmap(targetWidth, targetHeight)

            val device = AndroidDrawDevice(bitmap, 0, 0)
            page.run(device, Matrix(scaleX, scaleY), null)

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

    private suspend fun generateBookCoverThumbnail(
        sha: String,
        document: Document,
        bookTitle: String?,
        bookAuthor: String?,
        targetWidth: Int = 200,
        targetHeight: Int = 300
    ): Boolean {
        return try {
            val hashName = "${sha}.png"
            val thumbnailFile = File(this.context.filesDir, hashName)
            if (thumbnailFile.exists()) return true

            val firstPage = FunctionalStructuredTextWalker().getPageCoordinates(document, 0)
            val generated = if (firstPage.imageIsFullPage) {
                generateBookCoverFromFirstPage(
                    thumbnailFile, document, targetWidth, targetHeight
                )
            } else if (bookTitle != null && bookAuthor != null) {
                downloadAndSaveCover(
                    bookTitle, bookAuthor, thumbnailFile, targetWidth, targetHeight
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

    fun loadCategories(): List<Category> {
        val list = mutableListOf<Category>()
        val db = writableDatabase
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

    fun updateBookCategory(bookId: Long, bookColumn: String, categoryId: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(bookColumn, categoryId)
        }
        db.update("books", values, "id = ?", arrayOf(bookId.toString()))
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

    fun getCategoriesForBookmarks(): List<String> {
        val db = readableDatabase
        val list = mutableListOf<String>()
        val sql = """
            SELECT c.category  AS used_categories
            FROM books AS b
            LEFT JOIN categories AS c
              ON c.id = b.category
			  LEFT JOIN bookmarks AS m
			  ON m.book_id_fk = b.id
            WHERE c.category IS NOT NULL  AND m.id IS NOT NULL
            
            UNION
            
            SELECT sc.category
            FROM books AS b
            LEFT JOIN categories AS sc
              ON sc.id = b.sub_category
			  LEFT JOIN bookmarks AS sm
			  ON sm.book_id_fk = b.id
            WHERE sc.category IS NOT NULL AND sm.id IS NOT NULL
            
            ORDER BY used_categories
        """.trimIndent()
        val cursor = db.rawQuery(
            sql, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    fun getUsedCategories(activityName: String?): List<String> {
        if (activityName == "BookmarksActivity") {
            return getCategoriesForBookmarks()
        }
        val db = readableDatabase
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
            sql, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
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

    fun getBookShelfBooks(): List<FileItem> {
        val db = readableDatabase
        val list = mutableListOf<FileItem>()
        val sql = """
            SELECT DISTINCT b.sha AS book_sha, b.name AS book_name, c.category AS main_category, sc.category AS sub_category , b.location as book_location, b.author as author_name
            FROM  books AS b
            LEFT JOIN  categories AS c
            ON c.id = b.category
            LEFT JOIN  categories AS sc
            ON sc.id = b.sub_category
            ORDER BY (b.sub_category IS NULL) ASC, b.sub_category ASC, (b.category  IS NULL) ASC, b.category  ASC, b.author;
        """.trimIndent()
        val cursor = db.rawQuery(
            sql, null
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val sha = cursor.getString(cursor.getColumnIndexOrThrow("book_sha"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("book_name"))
                val mainCategory = cursor.getString(cursor.getColumnIndexOrThrow("main_category"))
                val subCategory = cursor.getString(cursor.getColumnIndexOrThrow("sub_category"))
                val location = cursor.getString(cursor.getColumnIndexOrThrow("book_location"))
                val author = cursor.getString(cursor.getColumnIndexOrThrow("author_name"))
                val file = File(location)
                val bookInfo = BookInfo(sha, name, mainCategory, subCategory, author)
                list.add(FileItem(file, file.name, bookInfo, System.currentTimeMillis()))
            }
        }
        return list
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
                INSERT INTO toc (book_id_fk, parent_id, level, title, page, scale, translate)
                VALUES (?,?,?,?,?,?,?)
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
                    stmt.bindString(4, entry.title ?: "")
                    stmt.bindLong(5, page.toLong())
                    stmt.bindDouble(6, pageCoordinates.targetScale.toDouble())
                    stmt.bindDouble(7, pageCoordinates.translatingPercentage.toDouble())

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
                "category",
                "sub_category",
                "alternate_cover"
            ), "location = ?", arrayOf(location), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Book(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    sha = cursor.getString(cursor.getColumnIndexOrThrow("sha")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    isbn = cursor.getString(cursor.getColumnIndexOrThrow("isbn")),
                    scanned = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("scanned")) == 1,
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
            arrayOf("%$currentText%")
        } else {
            arrayOf("%$currentText%", currentCategory)
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
            LEFT JOIN categories AS c ON b.category = c.id OR b.sub_category = c.id
            WHERE t.title LIKE ? 
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
                t.page AS page_title, 
                t.level AS TOC_LEVEL, 
                t.bookmark_id AS bookmark_id,
                t.scale AS toc_scale, 
                translate AS toc_translate,
                b.name AS book_tite
            FROM toc AS t
            LEFT JOIN books AS  b
            ON t.book_id_fk = b.id
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
                        bookTitle = cursor.getString(8),
                        parentId = cursor.getLongOrNull(1),
                        title = cursor.getString(2),
                        page = cursor.getInt(3) - 1,
                        level = cursor.getInt(4),
                        bookmarkId = cursor.getIntOrNull(5),
                        scale = cursor.getFloat(6),
                        translate = cursor.getFloat(7),
                    )
                )
            }
        }
        return rows
    }

    fun createBookmark(item: BookmarkItem): Boolean {
        val db = writableDatabase
        val bookIdStr = item.bookId?.toString() ?: "NULL"
        val uniqueKey = "$bookIdStr|${item.title}|${item.page}|${item.offset}"
        val insertSql = """
        INSERT OR IGNORE INTO bookmarks 
        (book_id_fk, title, page, `offset`,  scale, translate, unique_key)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()


        val rowId = db.compileStatement(insertSql).use { stmt ->
            if (item.bookId != null) stmt.bindLong(1, item.bookId) else stmt.bindNull(1)
            stmt.bindString(2, item.title)
            stmt.bindLong(3, item.page.toLong())
            if (item.offset != null) stmt.bindLong(4, item.offset.toLong()) else stmt.bindNull(4)
            stmt.bindDouble(5, item.scale.toDouble())
            stmt.bindDouble(6, item.translate.toDouble())
            stmt.bindString(7, uniqueKey)

            stmt.executeInsert()
        }
        val success = rowId != -1L
        if (success) {
            val values = ContentValues().apply {
                put("bookmark_id", rowId)
            }
            db.update("toc", values, "id = ?", arrayOf(item.tocId.toString()))
        }
        return success
    }

    fun deleteBookmark(item: BookmarkItem): Boolean {
        val db = writableDatabase
        db.beginTransaction()

        return try {
            val deletedRows = db.delete(
                "bookmarks", "id = ?", arrayOf(item.bookmarkId.toString())
            )

            if (deletedRows > 0) {
                db.execSQL(
                    "UPDATE toc SET bookmark_id = NULL WHERE id = ?", arrayOf(item.tocId)
                )

                db.setTransactionSuccessful()
                true
            } else {
                false
            }
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
                m.page AS bookmark_page, 
                m.`offset` AS bookmark_offset, 
                m.scale AS bookmark_scale, 
                m.translate AS bookmark_translate,
                b.name AS book_tite,
                b.location AS book_location
            FROM bookmarks AS m
            LEFT JOIN books AS  b
            ON m.book_id_fk = b.id
            LEFT JOIN categories AS c ON b.category = c.id OR b.sub_category = c.id
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
                        bookTitle = cursor.getString(7),
                        bookId = cursor.getLongOrNull(1),
                        page = cursor.getInt(3),
                        offset = cursor.getIntOrNull(4),
                        scale = cursor.getFloat(5),
                        translate = cursor.getFloat(6),
                        bookLocation = cursor.getStringOrNull(8),
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
                        bookTitle = cursor.getString(7),
                        bookId = cursor.getLongOrNull(1),
                        page = cursor.getInt(3),
                        offset = cursor.getIntOrNull(4),
                        scale = cursor.getFloat(5),
                        translate = cursor.getFloat(6),
                        bookLocation = cursor.getStringOrNull(7),
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
}
