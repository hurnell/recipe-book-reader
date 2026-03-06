package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.TocItem

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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_toc_every, parent, false)
        return TocViewHolder(view)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            onClickTitle,
            onClickBook,
            onClickHierarchy,
            onLongClickTitle,
            onClickBookmark
        )
    }

    class TocViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tocText: TextView = itemView.findViewById(R.id.tocText)
        private val bookName: TextView = itemView.findViewById(R.id.bookName)
        private val hierarchy: TextView = itemView.findViewById(R.id.hierarchy)
        private val hasBookmark: ImageView = itemView.findViewById(R.id.hasBookmark)

        fun bind(
            item: TocItem,
            onClickTitle: (TocItem) -> Unit,
            onClickBook: (TocItem) -> Unit,
            onClickHierarchy: (TocItem) -> Unit,
            onLongClickTitle: (TocItem) -> Unit,
            onClickBookmark: (TocItem) -> Unit,
        ) {
            tocText.text = item.title
            bookName.text = item.bookTitle ?: ""
            val hierarchyText = item.hierarchy ?: ""

            if (hierarchyText.isEmpty()) {
                hierarchy.visibility = View.GONE
            } else {
                hierarchy.text = hierarchyText
                hierarchy.visibility = View.VISIBLE
            }

            tocText.setOnClickListener {
                onClickTitle(item)
            }

            tocText.setOnLongClickListener {
                if (tocText.isEllipsized()) onLongClickTitle.invoke(item)
                true
            }

            bookName.setOnClickListener {
                if (bookName.isEllipsized()) onClickBook(item)
            }

            hierarchy.setOnClickListener {
                if (hierarchy.isEllipsized()) onClickHierarchy(item)
            }
            hasBookmark.setOnClickListener {
                onClickBookmark(item)
            }
            if (item.bookmarkId != null) {
                hasBookmark.setImageResource(R.drawable.ic_bookmark_closed)
            } else {
                hasBookmark.setImageResource(R.drawable.ic_bookmark_open)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TocItem>() {
        override fun areItemsTheSame(oldItem: TocItem, newItem: TocItem): Boolean =
            oldItem.tocId == newItem.tocId

        override fun areContentsTheSame(oldItem: TocItem, newItem: TocItem): Boolean =
            oldItem == newItem
    }
}