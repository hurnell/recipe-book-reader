package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.BookmarkItem

class BookmarkAdapter(
    private var fullList: List<BookmarkItem>,
    private val onClick: (BookmarkItem) -> Unit,
    private val onDeleteClick: (BookmarkItem) -> Unit,
    private val onLongClick: (BookmarkItem) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bookmarkTitle: TextView = view.findViewById(R.id.bookmarkTitle)
        val bookmarkPage: TextView = view.findViewById(R.id.bookmarkPage)
        val deleteBookmarkIcon: ImageButton = view.findViewById(R.id.deleteBookmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_bookmark, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: BookmarkViewHolder,
        position: Int
    ) {
        val item = fullList[position]
        holder.bookmarkTitle.text = item.title
        holder.bookmarkPage.text = item.page.toString()
        holder.bookmarkTitle.setOnClickListener { onClick(item) }
        holder.deleteBookmarkIcon.setOnClickListener { onDeleteClick(item) }
        holder.bookmarkTitle.setOnLongClickListener {
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