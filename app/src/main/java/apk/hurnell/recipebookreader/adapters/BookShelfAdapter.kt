package apk.hurnell.recipebookreader.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.model.FileItem
import com.google.android.material.card.MaterialCardView
import java.io.File

class BookShelfAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: ((File) -> Unit)? = null
) : ListAdapter<List<FileItem?>, BookShelfAdapter.RowViewHolder>(RowDiffCallback()) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.book_shelf_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val rowItems = getItem(position)

        bindBook(
            holder.book1Cover,
            holder.book1Warning,
            rowItems.getOrNull(0),
            onClick,
            onLongClick
        )
        bindBook(
            holder.book2Cover,
            holder.book2Warning,
            rowItems.getOrNull(1),
            onClick,
            onLongClick
        )
        bindBook(
            holder.book3Cover,
            holder.book3Warning,
            rowItems.getOrNull(2),
            onClick,
            onLongClick
        )
    }

    private fun bindBook(
        imageView: ImageView,
        warningView: ImageView,
        item: FileItem?,
        onClick: (File) -> Unit,
        onLongClick: ((File) -> Unit)?
    ) {
        val cardContainer = imageView.parent as MaterialCardView

        var thumbnailFile: File? = null
        if (item != null && item.bookInfo != null) {
            thumbnailFile = File(imageView.context.filesDir, "${item.bookInfo.sha}.png")
        }

        if (item != null && thumbnailFile != null && thumbnailFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)

            imageView.setImageBitmap(bitmap)
            if (item.bookInfo?.mainCategory == null || item.bookInfo.subCategory == null) {
                warningView.visibility = View.VISIBLE
            } else {
                warningView.visibility = View.INVISIBLE
            }
            imageView.visibility = View.VISIBLE
            imageView.setOnClickListener { onClick(item.file) }
            imageView.setOnLongClickListener {
                onLongClick?.invoke(item.file)
                true
            }
            cardContainer.visibility = View.VISIBLE
        } else {
            imageView.setImageDrawable(null)
            imageView.visibility = View.INVISIBLE
            imageView.setOnClickListener(null)
            cardContainer.visibility = View.INVISIBLE
            warningView.visibility = View.INVISIBLE

            imageView.setOnLongClickListener(null)

        }

        if (item == null) {
            imageView.visibility = View.GONE
        }
    }

    fun updateCoverForSha(updatedSha: String) {
        val newList = currentList.map { row ->
            row.map { item ->
                if (item != null && item.bookInfo?.sha == updatedSha) {
                    item.copy(lastModified = System.currentTimeMillis())
                } else item
            }
        }
        submitList(newList)
    }

    class RowDiffCallback : DiffUtil.ItemCallback<List<FileItem?>>() {
        override fun areItemsTheSame(oldItem: List<FileItem?>, newItem: List<FileItem?>): Boolean {
            val oldId = oldItem.joinToString { it?.file?.absolutePath.toString() }
            val newId = newItem.joinToString { it?.file?.absolutePath.toString() }
            return oldId == newId
        }

        override fun areContentsTheSame(
            oldItem: List<FileItem?>,
            newItem: List<FileItem?>
        ): Boolean {
            val oldId = oldItem.joinToString { it?.file?.absolutePath.toString() }
            val newId = newItem.joinToString { it?.file?.absolutePath.toString() }
            val oldLastModified = oldItem.map { it?.lastModified }.joinToString()
            val newLastModified = newItem.map { it?.lastModified }.joinToString()
            return oldId == newId && oldLastModified == newLastModified
        }
    }

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val book1Cover: ImageView = view.findViewById<View>(R.id.book1).findViewById(R.id.bookCover)
        val book2Cover: ImageView = view.findViewById<View>(R.id.book2).findViewById(R.id.bookCover)
        val book3Cover: ImageView = view.findViewById<View>(R.id.book3).findViewById(R.id.bookCover)
        val book1Warning: ImageView =
            view.findViewById<View>(R.id.book1).findViewById(R.id.bookWarning)
        val book2Warning: ImageView =
            view.findViewById<View>(R.id.book2).findViewById(R.id.bookWarning)
        val book3Warning: ImageView =
            view.findViewById<View>(R.id.book3).findViewById(R.id.bookWarning)
    }
}