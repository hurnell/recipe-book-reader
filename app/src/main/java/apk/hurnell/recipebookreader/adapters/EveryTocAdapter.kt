package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.TocItem
import apk.hurnell.recipebookreader.databinding.ListItemTocEveryBinding

fun TextView.isEllipsized(): Boolean {
    val l = this.layout
    return if (l != null) {
        val lines = l.lineCount
        if (lines > 0) l.getEllipsisCount(lines - 1) > 0 else false
    } else false
}

class EveryTocAdapter(
    private val onLongClickTitle: (TocItem) -> Unit,
    private val onClickTitle: (TocItem) -> Unit,
    private val onClickBook: (TocItem) -> Unit,
    private val onClickHierarchy: (TocItem) -> Unit,
    private val onClickBookmark: (TocItem) -> Unit,
) : ListAdapter<TocItem, EveryTocAdapter.TocViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
        val binding =
            ListItemTocEveryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TocViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tocText.text = item.title
        holder.binding.bookName.text = item.bookTitle ?: ""
        val hierarchyText = item.hierarchy ?: ""
        if (hierarchyText.isEmpty()) {
            holder.binding.hierarchy.visibility = View.GONE
        } else {
            holder.binding.hierarchy.text = hierarchyText
            holder.binding.hierarchy.visibility = View.VISIBLE
        }
        holder.binding.tocText.setOnClickListener {
            onClickTitle(item)
        }
        holder.binding.tocText.setOnLongClickListener {
            if (holder.binding.tocText.isEllipsized()) onLongClickTitle.invoke(item)
            true
        }
        holder.binding.bookName.setOnClickListener {
            if (holder.binding.bookName.isEllipsized()) onClickBook(item)
        }

        holder.binding.hierarchy.setOnClickListener {
            if (holder.binding.hierarchy.isEllipsized()) onClickHierarchy(item)
        }
        holder.binding.hasBookmark.setOnClickListener {
            onClickBookmark(item)
        }
        if (item.bookmarkId != null) {
            holder.binding.hasBookmark.setImageResource(R.drawable.ic_bookmark_closed)
        } else {
            holder.binding.hasBookmark.setImageResource(R.drawable.ic_bookmark_open)
        }
    }

    class TocViewHolder(val binding: ListItemTocEveryBinding) :
        RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<TocItem>() {
        override fun areItemsTheSame(oldItem: TocItem, newItem: TocItem): Boolean =
            oldItem.tocId == newItem.tocId

        override fun areContentsTheSame(oldItem: TocItem, newItem: TocItem): Boolean =
            oldItem == newItem
    }
}