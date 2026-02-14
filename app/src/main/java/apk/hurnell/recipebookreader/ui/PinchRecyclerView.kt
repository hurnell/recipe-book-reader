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
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var translationX = 0f
    private var translationY = 0f

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

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastFocusX = ev.x
                lastFocusY = ev.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = ev.x - lastFocusX
                    val dy = ev.y - lastFocusY
                    translationX += dx
                    translationY += dy
                    fixTranslation()
                    invalidate()
                    lastFocusX = ev.x
                    lastFocusY = ev.y
                }
            }
        }
        return super.onTouchEvent(ev) || true
    }

    private fun fixTranslation() {
        val maxTransX = 0f
        val maxTransY = 0f
        val minTransX = min(width - width * scaleFactor, 0f)
        val minTransY = min(height - height * scaleFactor, 0f)

        translationX = translationX.coerceIn(minTransX, maxTransX)
        translationY = translationY.coerceIn(minTransY, maxTransY)
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
