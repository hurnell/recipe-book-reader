package apk.hurnell.recipebookreader.helpers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfRepository(
    private val contentResolver: ContentResolver,
    private val context: Context
) {

    suspend fun openDocument(uri: Uri): Document =
        withContext(Dispatchers.IO) {
            val stream = PdfStreamer(contentResolver, uri)
            Document.openDocument(stream, "application/pdf")
        }

    suspend fun checkOrCreateBook(
        file: File,
        path: String,
        document: Document
    ): Long = withContext(Dispatchers.IO) {
        DatabaseHelper(context).checkAddBookToDatabase(file, path, document)
    }

    suspend fun hasToc(bookId: Long): Boolean =
        withContext(Dispatchers.IO) {
            DatabaseHelper(context).hasTableOfContents(bookId)
        }

    /**
     * IMPORTANT: opens a NEW Document instance
     * so MuPDF is not accessed concurrently.
     */
    suspend fun generateTocAsync(uri: Uri, bookId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val stream = PdfStreamer(contentResolver, uri)
            val tocDoc = Document.openDocument(stream, "application/pdf")

            try {
                DatabaseHelper(context).generateTOC(tocDoc, bookId)
            } finally {
                tocDoc.destroy()
            }
        }
}