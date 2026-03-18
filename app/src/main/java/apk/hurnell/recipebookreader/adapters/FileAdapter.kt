package apk.hurnell.recipebookreader.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BookInfo
import apk.hurnell.recipebookreader.model.FileItem
import apk.hurnell.recipebookreader.databinding.ListItemFileBinding
import kotlin.io.extension


class FileAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: ((File) -> Unit)? = null,
    private val repository: PdfRepository,
    private val pdfOnly: Boolean = true
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ListItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onLongClick, repository, pdfOnly)
    }

    class FileViewHolder(val binding: ListItemFileBinding) : RecyclerView.ViewHolder(binding.root) {

        fun updateIconLayout(fileIcon: ImageView, hasCover: Boolean) {
            val layoutParams = fileIcon.layoutParams

            val context = fileIcon.context
            val density = context.resources.displayMetrics.density
            if (hasCover) {
                layoutParams.width = 200
                layoutParams.height = 300
            } else {
                layoutParams.width = (40 * density).toInt()
                layoutParams.height = (40 * density).toInt()
            }
            fileIcon.layoutParams = layoutParams
        }

        fun isFileType(extension: String, pdfOnly: Boolean): Boolean {
            val isPdf = extension.equals("pdf", true)
            if (pdfOnly) {
                return isPdf
            }
            val isPng = extension.equals("png", true)
            val isJpg = extension.equals("jpg", true)
            val isJpeg = extension.equals("jpeg", true)
            return isJpg || isPng || isJpeg
        }

        fun getIconResource(file: File): Int {
            val extension = file.extension
            if (extension == "pdf") {
                return R.drawable.ic_pdf_file
            }
            if (extension == "png") {
                return R.drawable.ic_png_file
            }
            return R.drawable.ic_jpg_file
        }

        fun bind(
            item: FileItem,
            onClick: (File) -> Unit,
            onLongClick: ((File) -> Unit)?,
            repository: PdfRepository,
            pdfOnly: Boolean
        ) {
            binding.fileName.text = item.displayName

            val countText = if (item.file.isDirectory) {
                val children = item.file.listFiles()?.filter { child ->
                    child.canRead() && (child.isDirectory || isFileType(child.extension, pdfOnly))
                } ?: emptyList()

                "(${children.size})"
            } else {
                ""
            }
            var bookInfo: BookInfo? = null
            binding.innerCount.text = countText
            if (!item.file.isDirectory) {
                bookInfo = repository.getBookInfoForItemPath(item.file.path)
            }
            if (item.file.isDirectory) {
                updateIconLayout(binding.fileIcon, false)
                binding.fileIcon.setImageResource(R.drawable.ic_folder)
            } else if (bookInfo == null) {
                updateIconLayout(binding.fileIcon, false)

                binding.fileIcon.setImageResource(getIconResource(item.file))
            } else {
                if (bookInfo.name != "") {
                    binding.fileName.text = bookInfo.name
                }
                bookInfo.let { info ->
                    val thumbnailFile = File(itemView.context.filesDir, "${info.sha}.png")

                    if (thumbnailFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)

                        binding.fileIcon.setImageBitmap(bitmap)
                        updateIconLayout(binding.fileIcon, true)

                    } else {
                        binding.fileIcon.setImageResource(R.drawable.ic_pdf_file)
                    }
                }
            }


            itemView.setOnClickListener { onClick(item.file) }
            itemView.setOnLongClickListener {
                if (item.bookInfo != null) {
                    onLongClick?.invoke(item.file)
                }
                true
            }
        }

    }

    class DiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.displayName == newItem.displayName &&
                    oldItem.bookInfo?.sha == newItem.bookInfo?.sha &&
                    oldItem.lastModified == newItem.lastModified
        }
    }
}
