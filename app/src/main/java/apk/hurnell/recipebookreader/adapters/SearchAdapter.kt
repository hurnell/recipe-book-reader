package apk.hurnell.recipebookreader.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.databinding.ListItemSearchResultBinding
import apk.hurnell.recipebookreader.helpers.SearchResult
import  apk.hurnell.recipebookreader.R

class SearchAdapter(private val onResultClick: (SearchResult) -> Unit) :
    RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    private val results = mutableListOf<SearchResult>()

    fun addResult(result: SearchResult) {
        results.add(result)
        notifyItemInserted(results.size - 1)
    }

    fun clear() {
        val size = results.size
        results.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ListItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = results[position]
        holder.binding.txtPageNumber.text =
            holder.itemView.context.getString(R.string.page_number, item.pageIndex + 1)
        holder.binding.txtSnippet.text = item.text
        holder.itemView.setOnClickListener { onResultClick(item) }
    }

    override fun getItemCount() = results.size

    class ViewHolder(val binding: ListItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)
}