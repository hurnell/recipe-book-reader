package apk.hurnell.recipebookreader.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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

    //private lateinit var backCallback: OnBackPressedCallback

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

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            // Ensure this view can take focus so the searchField can give it up
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        root.fitsSystemWindows = false
        root.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        root.isFocusableInTouchMode = true
        val btnClear = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Standard Android "X"
            background = null
            visibility = View.GONE // Hidden initially
        }
        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            clipToPadding = false
        }
        adapter = TocAdapter(tocData) { page ->
            hideKeyboard()
            onPageSelected?.invoke(page)
        }
        recyclerView.adapter = adapter

        val controlBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor("#F5F5F5".toColorInt())
        }

        val btnToggle = ImageButton(context).apply {
            setImageResource(R.drawable.ic_chevron_right)
            background = null
            setOnClickListener {
                allExpanded = !allExpanded
                toggleAll(tocData, allExpanded)
                adapter?.updateVisibleItems()
                setImageResource(if (allExpanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right)
            }
        }

        val searchField = EditText(context).apply {
            tag = "search_field"
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            hint = "Search toc..."
            background = null
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
                    recyclerView.scrollToPosition(0)

                    // Show clear button only if text is not empty
                    btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
        btnClear.setOnClickListener {
            searchField.text.clear()
            hideKeyboard()      // Calls your existing hideKeyboard function
            searchField.clearFocus()
        }
        searchField.setOnEditorActionListener { v, _, _ ->
            hideKeyboard()
            v.clearFocus()
            true
        }
        controlBar.addView(btnToggle)
        controlBar.addView(searchField)
        controlBar.addView(btnClear)

        root.addView(recyclerView)
        root.addView(controlBar)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            val bottomInset = systemBars.bottom.coerceAtLeast(imeInsets.bottom)
            root.setPadding(0, 0, 0, bottomInset)

            insets
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize the callback as DISABLED


        // 2. Link the callback state to the Search Field focus
        // We find the searchField we created in onCreateView
        // Add .tag = "search_field" in onCreateView

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

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager

        // Crucial: clear focus so the OnFocusChangeListener triggers
        val focusedView = view?.findFocus()
        focusedView?.clearFocus()

        imm.hideSoftInputFromWindow(view?.windowToken, 0)
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