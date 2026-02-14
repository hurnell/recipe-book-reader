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

class PinchRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private var scaleFactor = 1f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var translationX = 0f
    private var mActivePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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

    private val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
            if (scaleFactor > 1f) {
                this@PinchRecyclerView.fling(0, -(vY / scaleFactor).toInt())
                return true
            }
            return false
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean = performClick()
    })

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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}