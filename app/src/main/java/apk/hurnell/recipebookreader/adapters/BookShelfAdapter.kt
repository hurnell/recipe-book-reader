package apk.hurnell.recipebookreader.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.model.FileItem
import com.google.android.material.card.MaterialCardView
import apk.hurnell.recipebookreader.databinding.ListItemBookShelfRowBinding
import apk.hurnell.recipebookreader.databinding.ListItemBookShelfItemBinding
import java.io.File

class BookShelfAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: ((File) -> Unit)? = null
) : ListAdapter<List<FileItem?>, BookShelfAdapter.RowViewHolder>(RowDiffCallback()) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ListItemBookShelfRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val rowItems = getItem(position)
        val books = listOf(holder.book1, holder.book2, holder.book3)

        books.forEachIndexed { index, binding ->
            bindBook(binding, rowItems.getOrNull(index) , onClick, onLongClick)
        }
    }

    private fun bindBook(
        binding: ListItemBookShelfItemBinding,
        item: FileItem?,
        onClick: (File) -> Unit,
        onLongClick: ((File) -> Unit)?
    ) {
        val imageView = binding.bookCover
        val warningView = binding.bookWarning
        val cardContainer = binding.bookCover.parent as MaterialCardView

        var thumbnailFile: File? = null
        if (item != null && item.bookInfo != null) {
            thumbnailFile = File(imageView.context.filesDir, "${item.bookInfo.sha}.png")
        }

        if (item != null && thumbnailFile != null && thumbnailFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)

            binding.bookCover.setImageBitmap(bitmap)
            if (item.bookInfo?.mainCategory == null || item.bookInfo.subCategory == null) {
                warningView.visibility = View.VISIBLE
            } else {
                warningView.visibility = View.INVISIBLE
            }
            binding.bookCover.visibility = View.VISIBLE
            binding.bookCover.setOnClickListener { onClick(item.file) }
            binding.bookCover.setOnLongClickListener {
                onLongClick?.invoke(item.file)
                true
            }
            cardContainer.visibility = View.VISIBLE
        } else {
            binding.bookCover.setImageDrawable(null)
            binding.bookCover.visibility = View.INVISIBLE
            binding.bookCover.setOnClickListener(null)
            cardContainer.visibility = View.INVISIBLE
            binding.bookWarning.visibility = View.INVISIBLE

            binding.bookCover.setOnLongClickListener(null)

        }

        if (item == null) {
            binding.bookCover.visibility = View.GONE
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

    class RowViewHolder(binding: ListItemBookShelfRowBinding) : RecyclerView.ViewHolder(binding.root) {
        val book1 = binding.book1
        val book2 = binding.book2
        val book3 = binding.book3
        /*val book1Cover: ImageView = view.findViewById<View>(R.id.book1).findViewById(R.id.bookCover)
        val book2Cover: ImageView = view.findViewById<View>(R.id.book2).findViewById(R.id.bookCover)
        val book3Cover: ImageView = view.findViewById<View>(R.id.book3).findViewById(R.id.bookCover)
        val book1Warning: ImageView =
            view.findViewById<View>(R.id.book1).findViewById(R.id.bookWarning)
        val book2Warning: ImageView =
            view.findViewById<View>(R.id.book2).findViewById(R.id.bookWarning)
        val book3Warning: ImageView =
            view.findViewById<View>(R.id.book3).findViewById(R.id.bookWarning)*/
    }
}