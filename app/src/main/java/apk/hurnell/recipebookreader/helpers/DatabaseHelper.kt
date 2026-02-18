package apk.hurnell.recipebookreader.helpers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest


class DatabaseHelper(private val context: Context) {

    companion object {
        private const val DB_NAME = "recipe-reader.db"
    }

    /**
     * Copies the database from assets to the app's databases folder if it does not exist.
     */
    @Throws(IOException::class)
    fun copyDatabaseIfNeeded() {
        val dbFile: File = context.getDatabasePath(DB_NAME)

        if (!dbFile.exists()) {
            // Ensure parent directories exist
            dbFile.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }

            // Copy database from assets using 'use' to auto-close streams
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * Opens the database safely.
     */
    fun openDatabase(): SQLiteDatabase {
        // Returns a non-null database object
        return context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
    }

    fun extractPageFromUri(uri: String?): Int {
        if (uri.isNullOrEmpty()) return 0  // default page if null

        // Regex to match "page=NUMBER"
        val regex = """page=(\d+)""".toRegex()
        val match = regex.find(uri)
        return match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    }


    fun insertOutline(
        document: Document,
        db: SQLiteDatabase,
        bookId: Long,
        outlineArray: Array<Outline>,
        parentId: Long? = null,
        level: Int = 0
    ) {
        val insertSql = """
        INSERT INTO toc (book_id_fk, parent_id, level, title, page, `offset`, scale, translate)
        VALUES (?,?,?,?,?,?,?,?)
    """.trimIndent()
        val stmt = db.compileStatement(insertSql)

        outlineArray.forEach { entry ->
            val page = extractPageFromUri(entry.uri)
            val pageCoordinates = FunctionalStructuredTextWalker().getPageCoordinates(document, page - 1)

            stmt.clearBindings()
            stmt.bindLong(1, bookId)
            parentId?.let { stmt.bindLong(2, it) } ?: stmt.bindNull(2)
            stmt.bindLong(3, level.toLong())
            stmt.bindString(4, entry.title ?: "")
            stmt.bindLong(5, page.toLong())
            stmt.bindDouble(6, pageCoordinates.leftOffset.toDouble())
            stmt.bindDouble(7, pageCoordinates.targetScale.toDouble())
            stmt.bindDouble(8, pageCoordinates.translatingPercentage.toDouble())

            val rowId = stmt.executeInsert() // rowId of this TOC entry

            // Recursively insert children
            if (!entry.down.isNullOrEmpty()) {
                insertOutline(document, db, bookId, entry.down, rowId, level + 1)
            }
        }

        stmt.close()
    }


    private fun iterateOutline(document: Document, outlineArray: Array<Outline>, level: Int = 0) {
        outlineArray.forEach { entry ->
            val page = extractPageFromUri(entry.uri)
            val pageCoordinates = FunctionalStructuredTextWalker().getPageCoordinates(document, page - 1)

            Log.e("NIGEL_HURNELL", "level: $level title: ${entry.title} page: $page leftOffset ${pageCoordinates.leftOffset} targetScale: ${pageCoordinates.targetScale}")
            Log.e("NIGEL_HURNELL", "translatingPercentage: ${pageCoordinates.translatingPercentage} found: ${pageCoordinates.found}")
            if (entry.down != null && entry.down.isNotEmpty()) {
                iterateOutline(document, entry.down, level + 1)
            }
        }
    }

    fun checkAddBookToDatabase(pdfFile: File, pdfFilePath: String, document: Document?): Boolean {
        if (document == null) return false

        val db = openDatabase()
        val sha256sum = pdfFile.sha256()
        val currentTimestamp = System.currentTimeMillis() / 1000  // Unix timestamp in seconds
        var dbUpdated = false
        var bookRowId: Long = 0
        var tocCreated = false

        try {
            // 1️⃣ Check if the book already exists
            val cursor = db.rawQuery(
                "SELECT rowid, location, toc_created FROM books WHERE sha = ?",
                arrayOf(sha256sum)
            )

            if (cursor.moveToFirst()) {
                // Book exists
                bookRowId = cursor.getLong(0)
                val existingLocation = cursor.getString(1)
                tocCreated = cursor.getInt(2) != 0
                val existingFile = File(existingLocation)

                if (!existingFile.exists()) {
                    // File at old location missing → update location and last_opened
                    db.execSQL(
                        "UPDATE books SET location = ?, last_opened = ? WHERE sha = ?",
                        arrayOf(pdfFilePath, currentTimestamp, sha256sum)
                    )
                    dbUpdated = true
                }
                // else: file exists, nothing to update
            } else {
                // Book does not exist → insert new
                val title = document.getMetaData(Document.META_INFO_TITLE) ?: ""
                val author = document.getMetaData(Document.META_INFO_AUTHOR) ?: ""
                db.execSQL(
                    "INSERT INTO books (sha, name, location, author, last_opened, toc_created) VALUES (?,?,?,?,?,?)",
                    arrayOf(sha256sum, title, pdfFilePath, author, currentTimestamp, 0)
                )
                // Get the inserted book rowid
                bookRowId = db.rawQuery("SELECT rowid FROM books WHERE sha = ?", arrayOf(sha256sum)).use { c ->
                    c.moveToFirst(); c.getLong(0)
                }
                dbUpdated = true
                tocCreated = false
            }
            cursor.close()

            // 2️⃣ Load outline / TOC only if not already created
            if (!tocCreated) {
                val outlineArray = try {
                    document.loadOutline()
                } catch (_: Exception) {
                    null
                }

                if (!outlineArray.isNullOrEmpty() && bookRowId != 0L) {
                    // 3️⃣ Insert TOC entries
                    insertOutline(document, db, bookRowId, outlineArray)
                    dbUpdated = true

                    // 4️⃣ Mark toc_created = 1
                    db.execSQL("UPDATE books SET toc_created = 1 WHERE sha = ?", arrayOf(sha256sum))
                }
            }

        } catch (e: Exception) {
            Log.e("NIGEL_HURNELL", "Database error: ${e.message}", e)
        } finally {
            db.close()
        }

        return dbUpdated
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
        // Convert to hex string
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
