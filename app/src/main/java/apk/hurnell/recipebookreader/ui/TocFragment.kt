package apk.hurnell.recipebookreader.ui

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline

data class TocItem(val title: String, val page: Int, val level: Int)

class TocFragment : Fragment() {

    private var onPageSelected: ((Int) -> Unit)? = null
    private val items = mutableListOf<TocItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            layoutManager = LinearLayoutManager(context)
            adapter = TocAdapter(items) { page -> onPageSelected?.invoke(page) }
        }
        return root
    }

    private fun extractToc(doc: Document) {
        items.clear()
        val outlineArray: Array<Outline>? = try {
            doc.loadOutline()
        } catch (e: Exception) {
            null
        }

        // Fix: Use an Array-based walk function
        fun walk(nodes: Array<Outline>?, level: Int) {
            if (nodes == null) return

            for (node in nodes) {
                // Resolve the page number from the outline node
                val pageNum = doc.pageNumberFromLocation(doc.resolveLink(node))

                items.add(TocItem(node.title ?: "Untitled", pageNum, level))

                // Recursively walk through children (node.down is usually an Array too)
                if (node.down != null) {
                    walk(node.down, level + 1)
                }
            }
        }

        walk(outlineArray, 0)
    }

    companion object {
        fun newInstance(doc: Document, listener: (Int) -> Unit): TocFragment {
            val fragment = TocFragment()
            fragment.onPageSelected = listener
            fragment.extractToc(doc)
            return fragment
        }
    }
}

class TocAdapter(private val list: List<TocItem>, private val onClick: (Int) -> Unit) :
    RecyclerView.Adapter<TocAdapter.TocViewHolder>() {

    class TocViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(48, 40, 48, 40)
            textSize = 16f
            setTextColor(Color.BLACK)
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        return TocViewHolder(tv)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        val item = list[position]
        // Visual indentation for sub-chapters
        holder.tv.setPadding(48 + (item.level * 48), 40, 48, 40)
        holder.tv.text = item.title
        holder.tv.setOnClickListener { onClick(item.page) }
    }

    override fun getItemCount() = list.size
}