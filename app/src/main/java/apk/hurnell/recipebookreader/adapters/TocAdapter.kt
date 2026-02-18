package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.ui.TocItem

class TocAdapter(
    private var fullList: List<TocItem>, // now var
    private val onClick: (TocItem) -> Unit
) : RecyclerView.Adapter<TocAdapter.TocViewHolder>() {

    private val visibleItems = mutableListOf<TocItem>()
    private var currentQuery = ""

    init {
        updateVisibleItems()
    }

    // New method to update TOC data dynamically
    fun updateData(newList: List<TocItem>) {
        fullList = newList
        updateVisibleItems()
    }

    fun filter(query: String) {
        currentQuery = query
        updateVisibleItems()
    }

    fun updateVisibleItems() {
        visibleItems.clear()
        if (currentQuery.isEmpty()) {
            fun collect(items: List<TocItem>) {
                for (item in items) {
                    visibleItems.add(item)
                    if (item.isExpanded && item.children.isNotEmpty()) {
                        collect(item.children)
                    }
                }
            }
            collect(fullList)
        } else {
            fun collectFiltered(items: List<TocItem>) {
                for (item in items) {
                    if (item.title.contains(currentQuery, ignoreCase = true)) {
                        visibleItems.add(item)
                    }
                    collectFiltered(item.children)
                }
            }
            collectFiltered(fullList)
        }
        notifyDataSetChanged()
    }

    class TocViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_toc, parent, false)
        return TocViewHolder(view)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        val item = visibleItems[position]
        holder.tvTitle.text = item.title

        val density = holder.itemView.resources.displayMetrics.density
        val level = if (currentQuery.isEmpty()) item.level else 0
        val indent = (level * 24 * density).toInt()
        holder.itemView.setPadding(indent + (16 * density).toInt(), 0, 0, 0)

        if (item.children.isEmpty() || currentQuery.isNotEmpty()) {
            holder.ivArrow.visibility = View.INVISIBLE
        } else {
            holder.ivArrow.visibility = View.VISIBLE
            holder.ivArrow.setImageResource(
                if (item.isExpanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right
            )
            holder.ivArrow.setOnClickListener {
                item.isExpanded = !item.isExpanded
                updateVisibleItems()
            }
        }

        holder.tvTitle.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = visibleItems.size
}
