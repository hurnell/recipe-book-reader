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

class EveryTocAdapter(

    private val onLongClickTitle: (TocItem) -> Unit,
    private val onClickTitle: (TocItem) -> Unit,
    private val onClickBook: (TocItem) -> Unit,
    private val onClickHierarchy: (TocItem) -> Unit
) : ListAdapter<TocItem, EveryTocAdapter.TocViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.every_toc_item, parent, false)
        return TocViewHolder(view)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        holder.bind(getItem(position),onClickTitle, onClickBook, onClickHierarchy, onLongClickTitle)
    }

    class TocViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tocText: TextView = itemView.findViewById(R.id.tocText)
        private val bookName: TextView = itemView.findViewById(R.id.bookName)
        private val hierarchy: TextView = itemView.findViewById(R.id.hierarchy)

        fun bind(
            item: TocItem,
            onClickTitle: (TocItem) -> Unit,
            onClickBook: (TocItem) -> Unit,
            onClickHierarchy: (TocItem) -> Unit,
            onLongClickTitle: (TocItem) -> Unit) {
            tocText.text = item.title
            bookName.text = item.bookTitle ?: ""
            val hierarchyText = item.hierarchy ?: ""
            if (hierarchyText == "") {
                hierarchy.visibility = View.GONE
            } else {
                hierarchy.text = hierarchyText
                hierarchy.visibility = View.VISIBLE
            }
            tocText.setOnClickListener { onClickTitle(item) }
            tocText.setOnLongClickListener {
                onLongClickTitle.invoke(item)
                true
            }
            bookName.setOnClickListener { onClickBook(item) }
            hierarchy.setOnClickListener { onClickHierarchy(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TocItem>() {
        override fun areItemsTheSame(oldItem: TocItem, newItem: TocItem): Boolean {
            return oldItem.tocId == newItem.tocId
        }

        override fun areContentsTheSame(oldItem: TocItem, newItem: TocItem): Boolean {
            return oldItem == newItem
        }
    }
}