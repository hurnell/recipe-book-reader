package helpers

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixValues = FloatArray(9)
    private val matrix = Matrix()
    private var scaleFactor = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId = -1

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                activePointerId = event.getPointerId(0)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    val dx = x - lastX
                    val dy = y - lastY

                    matrix.postTranslate(dx, dy)
                    fixTranslation()
                    imageMatrix = matrix

                    lastX = x
                    lastY = y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                performClick() // accessibility
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    // pick a new pointer
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastX = event.getX(newPointerIndex)
                    lastY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val newScale = scaleFactor * scale
            if (newScale in 1f..3f) {
                scaleFactor = newScale
                matrix.postScale(scale, scale, detector.focusX, detector.focusY)
                fixTranslation()
                imageMatrix = matrix
            }
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performClick()
            return true
        }
    }

    /** Keep image inside the view bounds */
    private fun fixTranslation() {
        val drawable = drawable ?: return
        val width = drawable.intrinsicWidth * scaleFactor
        val height = drawable.intrinsicHeight * scaleFactor

        matrix.getValues(matrixValues)
        var transX = matrixValues[Matrix.MTRANS_X]
        var transY = matrixValues[Matrix.MTRANS_Y]

        val minTransX = if (width > this.width) this.width - width else 0f
        val maxTransX = 0f
        val minTransY = if (height > this.height) this.height - height else 0f
        val maxTransY = 0f

        transX = min(maxTransX, max(minTransX, transX))
        transY = min(maxTransY, max(minTransY, transY))

        matrixValues[Matrix.MTRANS_X] = transX
        matrixValues[Matrix.MTRANS_Y] = transY
        matrix.setValues(matrixValues)
    }

}
