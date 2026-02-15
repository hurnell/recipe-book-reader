package apk.hurnell.recipebookreader.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.adapters.TocAdapter
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import androidx.core.graphics.toColorInt

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
    private var adapter: TocAdapter? = null
    private var allExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        // Root Layout
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // 1. The RecyclerView
        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            clipToPadding = false
        }
        adapter = TocAdapter(tocData) { page -> onPageSelected?.invoke(page) }
        recyclerView.adapter = adapter

        // 2. The Bottom Control Bar
        val controlBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor("#F5F5F5".toColorInt())
        }

        // 3. Toggle Expand/Collapse Button
        val btnToggle = ImageButton(context).apply {
            setImageResource(R.drawable.ic_unfold_less) // You'll need an icon for this
            background = null
            setOnClickListener {
                allExpanded = !allExpanded
                toggleAll(tocData, allExpanded)
                adapter?.updateVisibleItems()
                setImageResource(if (allExpanded) R.drawable.ic_unfold_less else R.drawable.ic_expand_more)
            }
        }

        // 4. Search EditText
        val searchField = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            hint = "Search recipes..."
            background = null // Minimalist look
            maxLines = 1
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    adapter?.filter(s.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }

        controlBar.addView(btnToggle)
        controlBar.addView(searchField)

        root.addView(recyclerView)
        root.addView(controlBar)

        // Handle Window Insets for the bottom bar
        ViewCompat.setOnApplyWindowInsetsListener(controlBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(16, 16, 16, systemBars.bottom + 16)
            insets
        }

        return root
    }

    private fun toggleAll(items: List<TocItem>, expand: Boolean) {
        items.forEach {
            it.isExpanded = expand
            toggleAll(it.children, expand)
        }
    }

    private fun extractToc(doc: Document): List<TocItem> {
        fun walk(nodes: Array<Outline>?, level: Int): List<TocItem> {
            if (nodes == null) return emptyList()
            return nodes.map { node ->
                val pageNum = try {
                    doc.pageNumberFromLocation(doc.resolveLink(node))
                } catch (_: Exception) {
                    0
                }
                val children = if (node.down != null) walk(node.down, level + 1) else emptyList()
                TocItem(node.title ?: "Untitled", pageNum, level, children)
            }
        }

        val outlineArray = try {
            doc.loadOutline()
        } catch (_: Exception) {
            null
        }
        return walk(outlineArray, 0)
    }

    companion object {
        fun newInstance(doc: Document, listener: (Int) -> Unit): TocFragment {
            val fragment = TocFragment()
            fragment.onPageSelected = listener
            fragment.tocData = fragment.extractToc(doc)
            return fragment
        }
    }
}