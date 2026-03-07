package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.TocItem
import apk.hurnell.recipebookreader.databinding.ListItemTocBinding

class TocAdapter(
    private var fullList: List<TocItem>,
    private val onClick: (TocItem) -> Unit,
    private val onLongClick: (TocItem) -> Unit
) : RecyclerView.Adapter<TocAdapter.TocViewHolder>() {

    private val visibleItems = mutableListOf<TocItem>()
    private var currentQuery = ""

    init {
        updateVisibleItems()
    }

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

    class TocViewHolder(val binding: ListItemTocBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
        val binding = ListItemTocBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TocViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        val item = visibleItems[position]
        holder.binding.tvTitle.text = item.title

        val density = holder.itemView.resources.displayMetrics.density
        val level = if (currentQuery.isEmpty()) item.level else 0
        val indent = (level * 6 * density).toInt()
        val left = indent + (2 * density).toInt()
        holder.itemView.setPadding(left, 0, 0, 0)

        if (item.children.isEmpty() || currentQuery.isNotEmpty()) {
            holder.binding.ivArrow.visibility = View.INVISIBLE
        } else {
            holder.binding.ivArrow.visibility = View.VISIBLE
            holder.binding.ivArrow.setImageResource(
                if (item.isExpanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right
            )
            holder.binding.ivArrow.setOnClickListener {
                item.isExpanded = !item.isExpanded
                updateVisibleItems()
            }
        }

        holder.binding.tvTitle.setOnClickListener { onClick(item) }
        holder.binding.tvTitle.setOnLongClickListener {
            onLongClick.invoke(item)
            true
        }
    }

    override fun getItemCount() = visibleItems.size
}
