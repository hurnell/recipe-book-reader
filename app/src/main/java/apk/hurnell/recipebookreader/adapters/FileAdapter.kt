package apk.hurnell.recipebookreader.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BookInfo
import apk.hurnell.recipebookreader.model.FileItem
import kotlin.io.extension


class FileAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: ((File) -> Unit)? = null,
    private val repository: PdfRepository,
    private val onlyPdf: Boolean = true
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onLongClick, repository, onlyPdf)
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val innerCount: TextView = itemView.findViewById(R.id.innerCount)

        fun updateIconLayout(fileIcon: ImageView, hasCover: Boolean){
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

        fun isFileType(extension: String, onlyPdf: Boolean): Boolean {
            val isPdf = extension.equals("pdf", true)
            if (onlyPdf) {
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
            if (extension == "png"){
                return R.drawable.ic_png_file
            }
            return R.drawable.ic_jpg_file
        }

        fun bind(
            item: FileItem,
            onClick: (File) -> Unit,
            onLongClick: ((File) -> Unit)?,
            repository: PdfRepository,
            onlyPdf: Boolean
        ) {
            fileName.text = item.displayName

            val countText = if (item.file.isDirectory) {
                val children = item.file.listFiles()?.filter { child ->
                    child.canRead() && (child.isDirectory || isFileType(child.extension, onlyPdf))
                } ?: emptyList()

                "(${children.size})"
            } else {
                ""
            }
            var bookInfo: BookInfo? = null
            innerCount.text = countText
            if (!item.file.isDirectory) {
                bookInfo = repository.getBookInfoForItemPath(item.file.path)
            }
            if (item.file.isDirectory) {
                updateIconLayout(fileIcon, false)
                fileIcon.setImageResource(R.drawable.ic_folder)
            } else if (bookInfo == null) {
                updateIconLayout(fileIcon, false)

                fileIcon.setImageResource(getIconResource(item.file))
            } else {
                fileName.text = bookInfo.name
                bookInfo.let { info ->
                    val thumbnailFile = File(itemView.context.filesDir, "${info.sha}.png")

                    if (thumbnailFile.exists()) {
                        // 1️⃣ Load bitmap from file
                        val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)

                        // 2️⃣ Set bitmap to ImageView
                        fileIcon.setImageBitmap(bitmap)
                        updateIconLayout(fileIcon, true)

                    } else {
                        // Fallback icon if thumbnail not yet created
                        fileIcon.setImageResource(R.drawable.ic_pdf_file)
                    }
                }
            }


            itemView.setOnClickListener { onClick(item.file) }
            itemView.setOnLongClickListener {
                if (item.bookInfo != null){
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
            // Compare name and bookInfo.sha (or thumbnail presence)
            return oldItem.displayName == newItem.displayName &&
                    oldItem.bookInfo?.sha == newItem.bookInfo?.sha &&
                    oldItem.lastModified == newItem.lastModified
        }
    }
}
