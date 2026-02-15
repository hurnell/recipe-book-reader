package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.ui.TocItem

class TocAdapter(
    private val fullList: List<TocItem>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<TocAdapter.TocViewHolder>() {

    private val visibleItems = mutableListOf<TocItem>()
    private var currentQuery = ""

    init {
        updateVisibleItems()
    }

    fun filter(query: String) {
        currentQuery = query
        updateVisibleItems()
    }

    fun updateVisibleItems() {
        visibleItems.clear()
        if (currentQuery.isEmpty()) {
            // Standard Hierarchical View
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
            // Flat Search Results View
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
        val root: View = view // Usually the root of the XML
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_toc, parent, false)
        return TocViewHolder(view)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        val item = visibleItems[position]
        holder.tvTitle.text = item.title

        // Indentation logic
        val density = holder.itemView.resources.displayMetrics.density
        // If searching, remove indentation to maximize space
        val level = if (currentQuery.isEmpty()) item.level else 0
        val indent = (level * 24 * density).toInt()
        holder.itemView.setPadding(indent + (16 * density).toInt(), 0, 0, 0)

        // Hide arrows during search results
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
        holder.tvTitle.setOnClickListener { onClick(item.page) }
    }

    override fun getItemCount() = visibleItems.size
}