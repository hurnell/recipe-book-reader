package apk.hurnell.recipebookreader.adapters

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleAdapter(
    private val items: List<String>
) : RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {

    // Scale factor for pinch-to-zoom
    var scaleFactor = 1f

    class ViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                120 // base item height
            )
            setPadding(16, 16, 16, 16)
            textSize = 18f
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view.text = items[position]

        // Apply scale factor both vertically and horizontally
        holder.view.scaleX = scaleFactor
        holder.view.scaleY = scaleFactor
    }

    override fun getItemCount(): Int = items.size
}
