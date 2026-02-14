package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class ZoomRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    // Needed for accessibility and to satisfy lint
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
