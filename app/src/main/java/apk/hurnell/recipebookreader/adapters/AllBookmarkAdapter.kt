package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.BookmarkItem

class AllBookmarkAdapter(
    private val onClick: (BookmarkItem) -> Unit,
    private val onDeleteClick: (BookmarkItem) -> Unit,
    private val onEditClick: (BookmarkItem) -> Unit,
    private val onLongClick: (BookmarkItem) -> Unit,
) : ListAdapter<BookmarkItem, AllBookmarkAdapter.BookmarkViewHolder>(AllBookmarksDiffCallback) {

    class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bookmarkTitle: TextView = view.findViewById(R.id.bookmarkTitle)
        val bookmarkPage: TextView = view.findViewById(R.id.bookmarkPage)
        val deleteBookmarkIcon: ImageButton = view.findViewById(R.id.deleteBookmark)
        val editBookmarkIcon: ImageButton = view.findViewById(R.id.editBookmark)
        val bookmarkBookTitle: TextView = view.findViewById(R.id.bookmarkBookTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_bookmark_all, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: BookmarkViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        holder.bookmarkTitle.text = item.title
        holder.bookmarkPage.text = item.page.toString()
        holder.bookmarkBookTitle.text = item.bookTitle
        holder.bookmarkTitle.setOnClickListener { onClick(item) }
        holder.deleteBookmarkIcon.setOnClickListener { onDeleteClick(item) }
        holder.editBookmarkIcon.setOnClickListener {
            item.position = position
            onEditClick(item)
        }
        holder.bookmarkTitle.setOnLongClickListener {
            onLongClick.invoke(item)
            true
        }
    }

    companion object {
        private val AllBookmarksDiffCallback = object : DiffUtil.ItemCallback<BookmarkItem>() {
            override fun areItemsTheSame(oldItem: BookmarkItem, newItem: BookmarkItem): Boolean {
                return oldItem.bookmarkId == newItem.bookmarkId
            }

            override fun areContentsTheSame(oldItem: BookmarkItem, newItem: BookmarkItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}