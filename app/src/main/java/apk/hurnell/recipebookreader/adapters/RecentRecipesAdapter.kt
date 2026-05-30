package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.model.BookmarkItem
import apk.hurnell.recipebookreader.databinding.ListItemRecentRecipeBinding
import apk.hurnell.recipebookreader.model.RecentRecipeItem

class RecentRecipesAdapter(
    private val onClick: (RecentRecipeItem) -> Unit,
    private val onLongClick: (RecentRecipeItem) -> Unit
) : ListAdapter<RecentRecipeItem, RecentRecipesAdapter.RecentRecipeViewHolder>(AllBookmarksDiffCallback) {

    class RecentRecipeViewHolder(val binding: ListItemRecentRecipeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentRecipeViewHolder {
        val binding =
            ListItemRecentRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecentRecipeViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: RecentRecipeViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        holder.binding.bookmarkTitle.text = item.title
        holder.binding.bookmarkPage.text = item.page.toString()
        holder.binding.bookmarkBookTitle.text = item.bookTitle
        holder.binding.bookmarkTitle.setOnClickListener { onClick(item) }

        holder.binding.bookmarkTitle.setOnLongClickListener {
            onLongClick.invoke(item)
            true
        }
    }

    companion object {
        private val AllBookmarksDiffCallback = object : DiffUtil.ItemCallback<RecentRecipeItem>() {
            override fun areItemsTheSame(oldItem: RecentRecipeItem, newItem: RecentRecipeItem): Boolean {
                return oldItem.recentRecipeId == newItem.recentRecipeId
            }

            override fun areContentsTheSame(oldItem: RecentRecipeItem, newItem: RecentRecipeItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}