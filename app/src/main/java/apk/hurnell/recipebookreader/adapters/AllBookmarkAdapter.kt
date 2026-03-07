package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.model.BookmarkItem
import apk.hurnell.recipebookreader.databinding.ListItemBookmarkAllBinding

class AllBookmarkAdapter(
    private val onClick: (BookmarkItem) -> Unit,
    private val onDeleteClick: (BookmarkItem) -> Unit,
    private val onEditClick: (BookmarkItem) -> Unit,
    private val onLongClick: (BookmarkItem) -> Unit,
) : ListAdapter<BookmarkItem, AllBookmarkAdapter.BookmarkViewHolder>(AllBookmarksDiffCallback) {

    class BookmarkViewHolder(val binding: ListItemBookmarkAllBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val binding =
            ListItemBookmarkAllBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: BookmarkViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        holder.binding.bookmarkTitle.text = item.title
        holder.binding.bookmarkPage.text = item.page.toString()
        holder.binding.bookmarkBookTitle.text = item.bookTitle
        holder.binding.bookmarkTitle.setOnClickListener { onClick(item) }
        holder.binding.deleteBookmark.setOnClickListener { onDeleteClick(item) }
        holder.binding.editBookmark.setOnClickListener {
            item.position = position
            onEditClick(item)
        }
        holder.binding.bookmarkTitle.setOnLongClickListener {
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