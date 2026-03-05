package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import com.bumptech.glide.Glide

class CoverPickerAdapter(
    private val urls: List<String>, private val onCoverSelected: (String) -> Unit
) : RecyclerView.Adapter<CoverPickerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.itemCoverImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_cover_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = urls[position]

        Glide.with(holder.itemView.context).load(url).into(holder.imageView)

        holder.itemView.setOnClickListener { onCoverSelected(url) }
    }

    override fun getItemCount() = urls.size
}