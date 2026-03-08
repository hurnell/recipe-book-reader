package apk.hurnell.recipebookreader.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import apk.hurnell.recipebookreader.helpers.IsbnFinder
import apk.hurnell.recipebookreader.helpers.PdfRepository
import java.io.File

class IsbnScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString("pdf_path") ?: return Result.failure()
        val bookId = inputData.getLong("book_id", -1L)
        if (bookId == -1L) return Result.failure()

        val repository = PdfRepository(applicationContext)
        val pdfFile = File(filePath)
        var document = repository.openPdfFast(pdfFile)

        return try {
            val foundIsbn = IsbnFinder().findIsbnInDocument(document)
            repository.updateBookIsbn(bookId, foundIsbn)
            Result.success()
        } catch (e: Exception) {
            Result.retry() // retry on failure
        } finally {
            document.destroy()
        }
    }
}