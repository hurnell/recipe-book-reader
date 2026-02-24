package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.FileItem // Adjust based on your package
class BookShelfAdapter : RecyclerView.Adapter<BookShelfAdapter.RowViewHolder>() {

    // A list of lists! Each inner list is exactly 3 items (some might be null)
    private var rows: List<List<FileItem?>> = emptyList()

    fun setRows(allFiles: List<FileItem?>) {
        // Chunk the flat list into groups of 3
        this.rows = allFiles.chunked(3)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.book_shelf_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val rowItems = rows[position]

        // Bind book 1
        bindBook(holder.book1Cover, rowItems.getOrNull(0))
        // Bind book 2
        bindBook(holder.book2Cover, rowItems.getOrNull(1))
        // Bind book 3
        bindBook(holder.book3Cover, rowItems.getOrNull(2))
    }

    private fun bindBook(imageView: ImageView, item: FileItem?) {
        if (item == null) {
            imageView.visibility = View.INVISIBLE
        } else {
            imageView.visibility = View.VISIBLE
            // Load your cover bitmap here
        }
    }

    override fun getItemCount(): Int = rows.size

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Find the ImageViews inside the included layouts
        val book1Cover: ImageView = view.findViewById<View>(R.id.book1).findViewById(R.id.bookCover)
        val book2Cover: ImageView = view.findViewById<View>(R.id.book2).findViewById(R.id.bookCover)
        val book3Cover: ImageView = view.findViewById<View>(R.id.book3).findViewById(R.id.bookCover)
    }
}