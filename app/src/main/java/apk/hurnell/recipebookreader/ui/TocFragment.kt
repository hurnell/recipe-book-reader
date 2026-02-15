package apk.hurnell.recipebookreader.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.TocAdapter
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline

data class TocItem(
    val title: String,
    val page: Int,
    val level: Int,
    val children: List<TocItem> = emptyList(),
    var isExpanded: Boolean = false
)

class TocFragment : Fragment() {

    private var onPageSelected: ((Int) -> Unit)? = null
    private var tocData: List<TocItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            layoutManager = LinearLayoutManager(context)
            adapter = TocAdapter(tocData) { page -> onPageSelected?.invoke(page) }

            // 1. Allow content to scroll behind the navigation bar area
            clipToPadding = false

            // 2. Listen for insets specifically for the drawer's content
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                // Apply bottom padding so the last item is above the navigation bar
                v.setPadding(0, 0, 0, systemBars.bottom)
                insets
            }
        }
        return root
    }

    private fun extractToc(doc: Document): List<TocItem> {
        fun walk(nodes: Array<Outline>?, level: Int): List<TocItem> {
            if (nodes == null) return emptyList()

            return nodes.map { node ->
                val pageNum = try {
                    doc.pageNumberFromLocation(doc.resolveLink(node))
                } catch (e: Exception) {
                    0
                }
                val children = if (node.down != null) walk(node.down, level + 1) else emptyList()

                TocItem(node.title ?: "Untitled", pageNum, level, children)
            }
        }

        val outlineArray = try {
            doc.loadOutline()
        } catch (e: Exception) {
            null
        }

        Log.i("TOC_DEBUG", "Outline size: ${outlineArray?.size ?: 0}")
        return walk(outlineArray, 0)
    }

    companion object {
        fun newInstance(doc: Document, listener: (Int) -> Unit): TocFragment {
            val fragment = TocFragment()
            fragment.onPageSelected = listener
            // CRITICAL: We must assign the result of extractToc to the fragment property
            fragment.tocData = fragment.extractToc(doc)
            return fragment
        }
    }
}