package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import apk.hurnell.recipebookreader.databinding.ItemCoverOptionBinding

class CoverPickerAdapter(
    private val urls: List<String>, private val onCoverSelected: (String) -> Unit
) : RecyclerView.Adapter<CoverPickerAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCoverOptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemCoverOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = urls[position]

        Glide.with(holder.binding.itemCoverImage.context).load(url)
            .into(holder.binding.itemCoverImage)

        holder.binding.itemCoverImage.setOnClickListener { onCoverSelected(url) }
    }

    override fun getItemCount() = urls.size
}