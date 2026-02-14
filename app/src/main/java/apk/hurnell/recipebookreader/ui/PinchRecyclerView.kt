package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withSave

class PinchRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private var scaleFactor = 1f
    private var posX = 0f
    private var posY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var maxWidth = 0f
    private var maxHeight = 0f
    private var widthPx = 0f
    private var heightPx = 0f
    private var activePointerId = INVALID_POINTER_ID
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    companion object {
        private const val INVALID_POINTER_ID = -1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        widthPx = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        heightPx = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        super.onTouchEvent(ev)
        scaleDetector.onTouchEvent(ev)

        val action = ev.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                val x = ev.getX(pointerIndex)
                val y = ev.getY(pointerIndex)
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                posX += dx
                posY += dy

                posX = posX.coerceIn(maxWidth, 0f)
                posY = posY.coerceIn(maxHeight, 0f)

                lastTouchX = x
                lastTouchY = y
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
                performClick() // accessibility
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = ev.getX(newPointerIndex)
                    lastTouchY = ev.getY(newPointerIndex)
                    activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withTranslation(posX, posY) {
            scale(scaleFactor, scaleFactor)
            super.onDraw(this)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withSave {
            if (scaleFactor == 1f) {
                posX = 0f
                posY = 0f
            }
            translate(posX, posY)
            scale(scaleFactor, scaleFactor)
            super.dispatchDraw(this)
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1f, 3f)

            maxWidth = widthPx - widthPx * scaleFactor
            maxHeight = heightPx - heightPx * scaleFactor

            invalidate()
            return true
        }
    }
}
