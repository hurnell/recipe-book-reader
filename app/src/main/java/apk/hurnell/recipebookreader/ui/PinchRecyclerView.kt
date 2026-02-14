package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min

class PinchRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private var scaleFactor = 1f
    private var translationX = 0f
    private var translationY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            scaleFactor *= scale
            scaleFactor = scaleFactor.coerceIn(1f, 3f)

            // Adjust translation so zoom centers on gesture
            val focusX = detector.focusX
            val focusY = detector.focusY
            translationX += (translationX - focusX) * (scale - 1)
            translationY += (translationY - focusY) * (scale - 1)

            fixTranslation()
            invalidate()
            return true
        }
    })

    private fun fixTranslation() {
        val maxTransX = 0f
        val maxTransY = 0f
        val minTransX = min(width - width * scaleFactor, 0f)
        val minTransY = min(height - height * scaleFactor, 0f)

        translationX = translationX.coerceIn(minTransX, maxTransX)
        translationY = translationY.coerceIn(minTransY, maxTransY)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                lastTouchX = ev.x
                lastTouchY = ev.y
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex != -1 && scaleFactor > 1f && !scaleDetector.isInProgress) {
                    val x = ev.getX(pointerIndex)
                    val y = ev.getY(pointerIndex)
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY

                    translationX += dx
                    translationY += dy
                    fixTranslation()

                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true // consume for panning
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = ev.getPointerId(ev.actionIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (ev.actionIndex == 0) 1 else 0
                    activePointerId = ev.getPointerId(newIndex)
                    lastTouchX = ev.getX(newIndex)
                    lastTouchY = ev.getY(newIndex)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                performClick()
            }
        }

        // If not zoomed, let RecyclerView scroll normally
        return scaleFactor > 1f || super.onTouchEvent(ev)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(translationX, translationY)
        canvas.scale(scaleFactor, scaleFactor)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
