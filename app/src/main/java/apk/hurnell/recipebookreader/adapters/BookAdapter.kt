package apk.hurnell.recipebookreader.adapters

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import androidx.core.graphics.createBitmap

class BookAdapter(
    private val document: Document
) :
    RecyclerView.Adapter<BookAdapter.PageViewHolder>() {

    class PageViewHolder(
        container: View,
        val imageView: ImageView,
        var pageScale: Float = 1.0f,
        var pageHeight: Int = 1,
        var pageWidth: Float = 1.0f
    ) :
        RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val context = parent.context

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setBackgroundColor(Color.WHITE)
        }

        val iv = ImageView(container.context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            adjustViewBounds = true
            isClickable = true
            isLongClickable = true
        }

        val divider = View(context).apply {
            val thickness = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics
            ).toInt()

            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH_PARENT, thickness)
            setBackgroundColor(Color.LTGRAY)
        }

        container.addView(iv)
        container.addView(divider)

        return PageViewHolder(container, iv)
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
        holder.pageScale = ((pageHeight * scale + 3).toInt()) / pageHeight
        holder.pageHeight = (pageHeight * 100).toInt()
        holder.pageWidth = pageWidth
        page.run(device, Matrix(scale, scale), null)

        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = 0xFFEEEEEE.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val boxWidth = 60f
        val boxHeight = 60f
        val left = (screenWidth - boxWidth) / 2f
        val top = 0f
        val right = left + boxWidth
        val bottom = top + boxHeight

        canvas.drawRect(left, top, right, bottom, paint)

        canvas.save()
        canvas.restore()

        device.close()
        device.destroy()
        page.destroy()

        holder.imageView.setImageBitmap(bitmap)

    }

    override fun getItemCount(): Int = document.countPages()
}