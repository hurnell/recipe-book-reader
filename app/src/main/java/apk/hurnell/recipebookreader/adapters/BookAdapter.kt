package apk.hurnell.recipebookreader.adapters

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import androidx.core.graphics.createBitmap
import com.artifex.mupdf.fitz.Rect
import androidx.core.graphics.toColorInt
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.NoteItem

class BookAdapter(
    private val document: Document,
    private var usableWidth: Int
) : RecyclerView.Adapter<BookAdapter.PageViewHolder>() {
    private var highlightedPage: Int = -1

    private val noteStarOutlinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    private val noteStarFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = "#FF00FF".toColorInt()
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    class PageViewHolder(
        container: View,
        val imageView: ImageView,
        var pageScale: Float = 1.0f,
        var pageHeight: Int = 1,
        var pageWidth: Float = 1.0f
    ) :
        RecyclerView.ViewHolder(container)

    fun updateUsableWidth(newWidth: Int) {
        this.usableWidth = newWidth
        notifyDataSetChanged()
    }

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

        val scale = usableWidth.toFloat() / pageWidth
        val bitmap = createBitmap(usableWidth, (pageHeight * scale).toInt())
        val device = AndroidDrawDevice(bitmap, 0, 0)
        holder.pageScale = ((pageHeight * scale + 3).toInt()) / pageHeight
        holder.pageHeight = (pageHeight * 100).toInt()
        holder.pageWidth = pageWidth
        page.run(device, Matrix(scale, scale), null)
        device.close()
        device.destroy()
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = 0xFFEEEEEE.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val boxWidth = 60f
        val boxHeight = 60f
        val left = (usableWidth - boxWidth) / 2f
        val top = 0f
        val right = left + boxWidth
        val bottom = top + boxHeight

        canvas.drawRect(left, top, right, bottom, paint)

        val highlightPaint = Paint().apply {
            color = ContextCompat.getColor(holder.itemView.context, R.color.book_highlight)
            alpha = 90
            style = Paint.Style.FILL
        }

        searchHighlights[position]?.forEach { rect ->
            val left = rect.x0 * scale
            val top = rect.y0 * scale
            val right = rect.x1 * scale
            val bottom = rect.y1 * scale

            canvas.drawRect(left, top, right, bottom, highlightPaint)
        }

        notesByPage[position]?.forEach { note ->
            val cx = note.x * scale
            val cy = note.y * scale
            canvas.drawText("★", cx, cy, noteStarOutlinePaint)
            canvas.drawText("★", cx, cy, noteStarFillPaint)
        }
        canvas.save()
        canvas.restore()

        page.destroy()

        holder.imageView.setImageBitmap(bitmap)
        holder.itemView.setBackgroundColor(
            if (position == highlightedPage) "#22FFFF00".toColorInt()
            else Color.TRANSPARENT
        )
    }

    fun clearHighlight() {
        searchHighlights.clear()
        highlightedPage = -1
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = document.countPages()

    private val searchHighlights = mutableMapOf<Int, List<Rect>>()

    fun setHighlight(pageIndex: Int, rectangles: List<Rect>?) {
        searchHighlights.clear()
        if (rectangles != null) {
            searchHighlights[pageIndex] = rectangles
        }
        highlightedPage = pageIndex
        notifyDataSetChanged()
    }

    private var notesByPage: Map<Int, List<NoteItem>> = emptyMap()

    fun setNotes(notes: Map<Int, List<NoteItem>>) {
        notesByPage = notes
        notifyDataSetChanged()
    }
}