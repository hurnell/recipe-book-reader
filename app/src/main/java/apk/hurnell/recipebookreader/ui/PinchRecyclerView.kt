package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min
import androidx.recyclerview.widget.LinearLayoutManager
import apk.hurnell.recipebookreader.RecipeBookTracker
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.BookHistoryItem

class PinchRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private var scaleFactor: Float = 1f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var translationX = 0f
    private var mActivePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1f, 3f)

                if (prevScale != scaleFactor) {
                    val scaleRatio = scaleFactor / prevScale
                    translationX = detector.focusX + (translationX - detector.focusX) * scaleRatio

                    val focusY = detector.focusY
                    scrollBy(0, ((focusY) * (scaleRatio - 1) / scaleFactor).toInt())
                }

                fixTranslation()
                invalidate()
                return true
            }
        })

    private val gestureDetector =
        GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (scaleFactor > 1f) {
                    this@PinchRecyclerView.fling(0, -(vY / scaleFactor).toInt())
                    return true
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean = performClick()
        })

    fun getScaleFactor(): Float = scaleFactor

    fun setScaleFactor(sf: Float, pageNumber: Int, translatingPercentage: Float): BookHistoryItem {
        scaleFactor = sf
        invalidate()
        translationX = (width * (1 - sf)) * translatingPercentage
        (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pageNumber, 0)
        invalidate()
        return BookHistoryItem(
            page = pageNumber,
            translationX = translationX,
            scale = sf
        )
    }

    fun handleReturnToRecipeBookTracker(tracker: RecipeBookTracker) {
        scaleFactor = tracker.scale!!
        invalidate()
        translationX = tracker.translationX!!
        (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(tracker.pageIndex!!, 0)
        invalidate()
    }
    fun handleReturnToRecipeBookHistoryItem(historyItem: BookHistoryItem) {
        scaleFactor = historyItem.scale!!
        invalidate()
        translationX = historyItem.translationX!!
        (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(historyItem.page, 0)
        invalidate()
    }

    data class TouchContext(
        val pageX: Float,
        val pageY: Float,
        val offset: Int,
        val scaleFactor: Float,
        val translationX: Float,
        val x: Float,
        val y: Float,
    ): BaseTracker()

    override fun getTranslationX(): Float = translationX

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = ev.actionIndex
                lastFocusX = ev.getX(pointerIndex)
                lastFocusY = ev.getY(pointerIndex)
                mActivePointerId = ev.getPointerId(0)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex != -1) {
                    val currentX = ev.getX(pointerIndex)
                    val currentY = ev.getY(pointerIndex)

                    if (!scaleDetector.isInProgress && scaleFactor > 1f) {
                        val dx = currentX - lastFocusX
                        val dy = currentY - lastFocusY

                        translationX += dx
                        scrollBy(0, (-dy / scaleFactor).toInt())

                        fixTranslation()
                        invalidate()
                    }
                    lastFocusX = currentX
                    lastFocusY = currentY
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)

                if (pointerId == mActivePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastFocusX = ev.getX(newPointerIndex)
                    lastFocusY = ev.getY(newPointerIndex)
                    mActivePointerId = ev.getPointerId(newPointerIndex)
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                mActivePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return if (scaleFactor > 1f) true else super.onTouchEvent(ev)
    }

    private fun fixTranslation() {
        val minTransX = min(width - width * scaleFactor, 0f)
        translationX = translationX.coerceIn(minTransX, 0f)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(translationX, 0f)
        canvas.scale(scaleFactor, scaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    val touchMetadata = HashMap<Long, TouchContext>()
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val child = findChildViewUnder(ev.x, ev.y)

        val location = IntArray(2)
        child?.getLocationInWindow(location)

        val offset = computeVerticalScrollOffset()
        val relativeX = ev.rawX - (location[0])
        val pageX = (relativeX - translationX) / scaleFactor
        val pageY = ev.y / scaleFactor + offset

        val context = TouchContext(
            pageX = pageX,
            pageY = pageY,
            offset = offset,
            scaleFactor = scaleFactor,
            translationX = translationX,
            x = ev.x,
            y = ev.y
        )
        touchMetadata[ev.eventTime] = context

        val handled = super.dispatchTouchEvent(ev)

        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            child?.postDelayed({ touchMetadata.remove(ev.eventTime) }, 100)
        }

        return handled
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}