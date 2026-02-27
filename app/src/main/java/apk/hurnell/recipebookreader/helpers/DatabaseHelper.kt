package apk.hurnell.recipebookreader.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
import android.graphics.Matrix as GraphicsMatrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import apk.hurnell.recipebookreader.model.BookInfo
import apk.hurnell.recipebookreader.model.FileItem
import androidx.core.graphics.createBitmap
import apk.hurnell.recipebookreader.model.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 1) {

    companion object {
        private const val DB_NAME = "recipe-reader.db"
        private val gson = Gson()
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Not used because we copy prebuilt DB from assets
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle migrations later if needed
    }

    override fun getWritableDatabase(): SQLiteDatabase {
        copyDatabaseIfNeeded()  // Copy database lazily, when DB is first accessed
        return super.getWritableDatabase()
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        copyDatabaseIfNeeded()  // Same here
        return super.getReadableDatabase()
    }

    fun saveConfiguration(key: String, data: Any) {
        val db = writableDatabase
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
        val db = writableDatabase
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
            Log.d(
                LOG_TAG,
                "Database not found, copying from assets..."
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
        file: File,
        path: String,
        document: Document
    ): Book? {
        val bookId = checkAddBookToDatabase(file, path, document)
        val db = writableDatabase


        return db.query(
            "books",
            arrayOf(
                "id", "sha", "name", "location", "author", "isbn",
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
                    isbn = cursor.getString(cursor.getColumnIndexOrThrow("isbn")),
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

    fun getKeywordCategories(keywords: String?): Pair<Int?, Int?> {

        val db = writableDatabase
        var mainCategoryId: Int? = null
        var subCategoryId: Int? = null

        if (!keywords.isNullOrBlank()) {
            // Split by comma into key=value pairs
            val pairs = keywords.split(",")
            for (pair in pairs) {
                val keyValue = pair.split("=").map { it.trim() }
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = keyValue[1]

                    // Prepare query
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
        file: File,
        path: String,
        document: Document
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
                            LOG_TAG,
                            "Failed to insert book! Check for column name mismatches."
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
    b.location AS book_location,
    t.id AS toc_id,
    t.parent_id AS parent_id,
    t.title AS toc_title,
    h.parent_path AS breadcrumbs, 
    t.page AS toc_page,
    t.level AS toc_level,
    t.`offset` AS toc_offset,
    t.scale AS toc_scale,
    t.translate AS toc_translate
FROM books AS b 
LEFT JOIN toc AS t ON b.id = t.book_id_fk 
LEFT JOIN toc_hierarchy h ON t.id = h.id
LEFT JOIN categories AS c ON b.category = c.id OR b.sub_category = c.id
WHERE t.title LIKE ? 
$categoryFilter
GROUP BY t.id
""".trimIndent()

        val cursor = db.rawQuery(sql, selectionArgs)

        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                // 1. Check for null title first
                val titleIndex = cursor.getColumnIndexOrThrow("toc_title")
                if (cursor.isNull(titleIndex)) continue

                val rawTitle = cursor.getString(titleIndex)

                // 2. Fix the breadcrumbs/hierarchy logic
                val breadcrumbs = cursor.getString(cursor.getColumnIndexOrThrow("breadcrumbs"))
                val hierarchyField = if (breadcrumbs.isNullOrBlank()) null else breadcrumbs

                // 3. Extract other fields
                val bookTitle = cursor.getString(cursor.getColumnIndexOrThrow("book_name"))
                val bookLocation = cursor.getString(cursor.getColumnIndexOrThrow("book_location"))
                val tocId = cursor.getLong(cursor.getColumnIndexOrThrow("toc_id"))
                val page = cursor.getInt(cursor.getColumnIndexOrThrow("toc_page"))
                val level = cursor.getInt(cursor.getColumnIndexOrThrow("toc_level"))

                val parentId = if (cursor.isNull(cursor.getColumnIndexOrThrow("parent_id")))
                    null else cursor.getInt(cursor.getColumnIndexOrThrow("parent_id"))

                val offset = if (cursor.isNull(cursor.getColumnIndexOrThrow("toc_offset")))
                    0f else cursor.getFloat(cursor.getColumnIndexOrThrow("toc_offset"))

                val scale = if (cursor.isNull(cursor.getColumnIndexOrThrow("toc_scale")))
                    1f else cursor.getFloat(cursor.getColumnIndexOrThrow("toc_scale"))

                val translate = if (cursor.isNull(cursor.getColumnIndexOrThrow("toc_translate")))
                    0f else cursor.getFloat(cursor.getColumnIndexOrThrow("toc_translate"))

                // 4. Build the Item
                list.add(
                    TocItem(
                        tocId = tocId,
                        bookTitle = bookTitle,
                        bookLocation = bookLocation,
                        parentId = parentId,
                        title = rawTitle,        // Just the clean title
                        hierarchy = hierarchyField, // Just the parents
                        page = page - 1,
                        level = level,
                        offset = offset,
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


            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generateBookCoverFromFirstPage(
        thumbnailFile: File,
        document: Document,
        targetWidth: Int,
        targetHeight: Int
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
            val thumbnailFile = File(this.context.filesDir, hashName)  // <--- use 'this.context'
            if (thumbnailFile.exists()) return true

            val firstPage = FunctionalStructuredTextWalker().getPageCoordinates(document, 0)
            val generated = if (firstPage.imageIsFullPage) {
                generateBookCoverFromFirstPage(
                    thumbnailFile,
                    document,
                    targetWidth,
                    targetHeight
                )
            } else if (bookTitle != null && bookAuthor != null) {
                downloadAndSaveCover(
                    bookTitle,
                    bookAuthor,
                    thumbnailFile,
                    targetWidth,
                    targetHeight
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

    fun getBookInfoForItemPath(location: String): BookInfo? {
        val db = readableDatabase
        return db.query(
            "books",
            arrayOf("sha", "name"),
            "location = ?",
            arrayOf(location),
            null, null, null
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
            sql,
            null
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
        document: Document,
        bookId: Long,
        progressCallback: ((percent: Int) -> Unit)? = null,
        ignoreTocParams: Boolean = false
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
                entries: List<Outline>,
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
                    val page = extractPageFromUri(entry.uri)
                    val pageCoordinates = FunctionalStructuredTextWalker().getPageCoordinates(
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
                            ignoredTranslate?.let {
                                minOf(
                                    it,
                                    pageCoordinates.translatingPercentage
                                )
                            }
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
            "books",
            arrayOf(
                "id", "sha", "name", "location", "author", "isbn",
                "last_opened", "toc_created", "toc_unavailable",
                "category", "sub_category", "alternate_cover"
            ),
            "location = ?",
            arrayOf(location),
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
                    isbn = cursor.getString(cursor.getColumnIndexOrThrow("isbn")),
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

    fun updateBookIsbn(bookId: Long, foundIsbn: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("isbn", foundIsbn)
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

        // We wrap the core logic in a COUNT(*) to let SQLite do the heavy lifting
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
            simpleQueryForLong() // Returns the first column of the first row
        }
    }
}
