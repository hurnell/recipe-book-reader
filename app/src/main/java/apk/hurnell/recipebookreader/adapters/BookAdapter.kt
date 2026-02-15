package apk.hurnell.recipebookreader.adapters

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import androidx.core.graphics.createBitmap

class BookAdapter(private val document: Document) :
    RecyclerView.Adapter<BookAdapter.PageViewHolder>() {

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val iv = ImageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            adjustViewBounds = true
            setBackgroundColor(Color.WHITE)
        }
        return PageViewHolder(iv)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = document.loadPage(position)
        val bounds = page.bounds
        val pageWidth = bounds.x1 - bounds.x0
        val pageHeight = bounds.y1 - bounds.y0

        val screenWidth = holder.imageView.resources.displayMetrics.widthPixels
        val scale = screenWidth / pageWidth

        val bitmap = createBitmap(screenWidth, (pageHeight * scale).toInt())
        val device = AndroidDrawDevice(bitmap, 0, 0)

        page.run(device, Matrix(scale, scale), null)

        device.close()
        device.destroy()
        page.destroy()

        holder.imageView.setImageBitmap(bitmap)
    }

    override fun getItemCount(): Int = document.countPages()
}