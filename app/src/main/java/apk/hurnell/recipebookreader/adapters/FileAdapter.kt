package apk.hurnell.recipebookreader.adapters

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
import apk.hurnell.recipebookreader.model.FileItem


class FileAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: ((File) -> Unit)? = null
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onLongClick)
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val innerCount: TextView = itemView.findViewById(R.id.innerCount)

        fun bind(item: FileItem, onClick: (File) -> Unit, onLongClick: ((File) -> Unit)?) {
            fileName.text = item.displayName

            val countText = if (item.file.isDirectory) {
                val children = item.file.listFiles()?.filter { child ->
                    child.canRead() && (child.isDirectory || child.extension.equals("pdf", true))
                } ?: emptyList()

                "(${children.size})"
            } else {
                ""
            }

            innerCount.text = countText

            fileIcon.setImageResource(
                if (item.file.isDirectory) R.drawable.ic_folder else R.drawable.ic_pdf_file
            )

            itemView.setOnClickListener { onClick(item.file) }
            itemView.setOnLongClickListener {
                onLongClick?.invoke(item.file)
                true
            }
        }

    }

    class DiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.displayName == newItem.displayName
        }
    }
}
