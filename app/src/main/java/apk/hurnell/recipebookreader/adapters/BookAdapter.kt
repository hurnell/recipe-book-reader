package apk.hurnell.recipebookreader.adapters

import android.content.ContentResolver
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

class BookAdapter(
    contentResolver: ContentResolver,
    pdfUri: Uri,
    cacheDir: File
) : RecyclerView.Adapter<BookAdapter.PageViewHolder>() {

    private var pdfRenderer: PdfRenderer? = null
    private var pageCount: Int = 0
    private val cachedFile: File

    init {
        val fileName = pdfUri.lastPathSegment ?: "temp.pdf"
        cachedFile = File(cacheDir, fileName)
        if (!cachedFile.exists()) {
            contentResolver.openInputStream(pdfUri)?.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val pfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(pfd)
        pageCount = pdfRenderer?.pageCount ?: 0
    }

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val drawable = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            setStroke(1, 0xFFE0E0E0.toInt())
        }

        val imageView = ImageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            background = drawable
        }
        return PageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        pdfRenderer?.let { renderer ->
            val page = renderer.openPage(position)

            val containerWidth = holder.imageView.width.takeIf { it > 0 }
                ?: holder.imageView.resources.displayMetrics.widthPixels
            val pageWidth = page.width.toFloat()
            val pageHeight = page.height.toFloat()

            val scale = containerWidth / pageWidth
            val bitmapWidth = (pageWidth * scale).toInt()
            val bitmapHeight = (pageHeight * scale).toInt()
            val bitmap = createBitmap(bitmapWidth, bitmapHeight)

            val matrix = Matrix().apply { setScale(scale, scale) }

            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            holder.imageView.setImageBitmap(bitmap)
        }
    }

    override fun getItemCount(): Int = pageCount

    fun close() {
        pdfRenderer?.close()
    }
}
