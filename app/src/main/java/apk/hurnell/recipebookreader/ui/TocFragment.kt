package apk.hurnell.recipebookreader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class TocFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // For now, just a placeholder. You can replace this with a RecyclerView later.
        return TextView(context).apply {
            text = "TOC Content Goes Here"
            textSize = 20f
            setPadding(32, 32, 32, 32)
        }
    }
}