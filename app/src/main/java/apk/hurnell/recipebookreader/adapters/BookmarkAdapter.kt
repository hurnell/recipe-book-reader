package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.databinding.ListItemBookmarkBinding
import apk.hurnell.recipebookreader.model.BookmarkItem

class BookmarkAdapter(
    private var fullList: List<BookmarkItem>,
    private val onClick: (BookmarkItem) -> Unit,
    private val onDeleteClick: (BookmarkItem) -> Unit,
    private val onLongClick: (BookmarkItem) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    class BookmarkViewHolder(val binding: ListItemBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val binding =
            ListItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: BookmarkViewHolder,
        position: Int
    ) {
        val item = fullList[position]
        holder.binding.bookmarkTitle.text = item.title
        holder.binding.bookmarkPage.text = item.page.toString()
        holder.binding.bookmarkTitle.setOnClickListener { onClick(item) }
        holder.binding.deleteBookmark.setOnClickListener { onDeleteClick(item) }
        holder.binding.bookmarkTitle.setOnLongClickListener {
            onLongClick.invoke(item)
            true
        }
    }

    override fun getItemCount(): Int = fullList.size

    fun updateData(bookmarkData: List<BookmarkItem>) {
        fullList = bookmarkData
        notifyDataSetChanged()
    }
}