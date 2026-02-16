package apk.hurnell.recipebookreader.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import androidx.core.graphics.createBitmap
import apk.hurnell.recipebookreader.ui.PinchRecyclerView

class BookAdapter(
    private val document: Document,
    private val onPageClicked: (pageNumber: Int, pageX: Float, pageY: Float) -> Unit,
    private val onPageLongClicked: (pageNumber: Int, pageX: Float, pageY: Float, pageWidth: Float, pageHeight: Float) -> Unit
) :
    RecyclerView.Adapter<BookAdapter.PageViewHolder>() {

    class PageViewHolder(container: android.view.View, val imageView: ImageView) :
        RecyclerView.ViewHolder(container)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val context = parent.context

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setBackgroundColor(Color.WHITE)
        }

        val iv = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            adjustViewBounds = true
            id = android.view.View.generateViewId()
        }

        val divider = android.view.View(context).apply {
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

        page.run(device, Matrix(scale, scale), null)

        device.close()
        device.destroy()
        page.destroy()

        holder.imageView.setImageBitmap(bitmap)
        val gestureDetector = GestureDetector(
            holder.imageView.context,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val parent = holder.imageView.parent as? PinchRecyclerView
                    val rvScale = parent?.getScaleFactor() ?: 1f
                    val rvTransX = parent?.getTranslationX() ?: 0f

                    val adjustedX = (e.x - rvTransX) / rvScale
                    val adjustedY = e.y / rvScale

                    val pageX = adjustedX / scale
                    val pageY = adjustedY / scale

                    val pageNumber = holder.bindingAdapterPosition
                    onPageClicked(pageNumber, pageX, pageY)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val parent = holder.imageView.parent as? PinchRecyclerView
                    val rvScale = parent?.getScaleFactor() ?: 1f
                    val rvTransX = parent?.getTranslationX() ?: 0f

                    val canvasX = e.x - rvTransX
                    val canvasY = e.y

                    val viewX = canvasX / rvScale
                    val viewY = canvasY / rvScale

                    val pageX = viewX / scale
                    val pageY = viewY / scale

                    onPageLongClicked(holder.bindingAdapterPosition, pageX, pageY, pageWidth, pageHeight)
                }
            }
        )
        @SuppressLint("ClickableViewAccessibility")
        holder.imageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            //val handled = gestureDetector.onTouchEvent(event)
            false
        }

    }

    override fun getItemCount(): Int = document.countPages()
}