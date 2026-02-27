package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.adapters.TocAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import apk.hurnell.recipebookreader.model.TocItem
import com.google.android.material.appbar.MaterialToolbar

class TocFragment : Fragment() {
    private lateinit var tocRecyclerView: RecyclerView
    private lateinit var tocSearchBar: LinearLayout
    private lateinit var toolbar: MaterialToolbar

    private var bookId: Int = -1
    private var onPageSelected: ((TocItem) -> Unit)? = null
    private var tocData: List<TocItem> = emptyList()
    private var adapter: TocAdapter? = null
    private var allExpanded = false

    companion object {
        fun newInstance(bookId: Int, listener: (TocItem) -> Unit): TocFragment {
            return TocFragment().apply {
                this.bookId = bookId
                this.onPageSelected = listener
            }
        }
    }

    private fun toggleVisibleChoices(showToc: Boolean) {

        val tocItem = toolbar.menu.findItem(R.id.action_show_toc)!!
        val bookmarkItem = toolbar.menu.findItem(R.id.action_show_bookmarks)!!
        tocItem.isVisible = !showToc
        bookmarkItem.isVisible = showToc
        tocSearchBar.visibility = if (showToc) View.VISIBLE else View.GONE
        tocRecyclerView.visibility = if (showToc) View.VISIBLE else View.GONE
        toolbar.title = if (showToc) "TOC" else "Bookmarks"
    }

    private fun setupToolbar(view: View) {
        toolbar = view.findViewById(R.id.tocToolbar)
        toolbar.inflateMenu(R.menu.toc_toolbar_menu)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        toolbar.setOnMenuItemClickListener { item ->
            toggleVisibleChoices(
                item.itemId == R.id.action_show_toc
            )
            true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_toc, container, false)
        setupToolbar(view)
        tocRecyclerView = view.findViewById(R.id.tocRecyclerView)
        tocSearchBar = view.findViewById(R.id.tocSearchBar)
        val searchField = view.findViewById<EditText>(R.id.searchField)
        val btnClear = view.findViewById<ImageButton>(R.id.btnClear)
        val btnToggle = view.findViewById<ImageButton>(R.id.btnToggle)

        tocRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        btnToggle.setOnClickListener {
            allExpanded = !allExpanded
            toggleAll(tocData, allExpanded)
            adapter?.updateVisibleItems()
            btnToggle.setImageResource(
                if (allExpanded) R.drawable.ic_expand_more
                else R.drawable.ic_chevron_right
            )
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter?.filter(s.toString())
                tocRecyclerView.scrollToPosition(0)
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        btnClear.setOnClickListener {
            searchField.text.clear()
            hideKeyboard()
            searchField.clearFocus()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TocAdapter(
            tocData,
            onClick = { item ->
                onPageSelected?.invoke(item)
                hideKeyboard()
            },
            onLongClick = { item ->
                Toast.makeText(
                    context,
                    "Please add  ${item.title} to bookmarks",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        val tocRecyclerView = view.findViewById<RecyclerView>(R.id.tocRecyclerView)
        tocRecyclerView.adapter = adapter

        loadTocAsync()
    }

    private fun loadTocAsync() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                context?.let { ctx -> loadTocFromDatabase(ctx, bookId) } ?: emptyList()
            }
            tocData = list
            adapter?.updateData(tocData)
        }
    }

    private fun loadTocFromDatabase(context: Context, bookId: Int): List<TocItem> {
        val db = context.openOrCreateDatabase("recipe-reader.db", 0, null)
        val cursor = db.rawQuery(
            """
            SELECT id, parent_id, title, page, level, `offset`, scale, translate
            FROM toc
            WHERE book_id_fk = ?
            ORDER BY id
        """.trimIndent(), arrayOf(bookId.toString())
        )

        data class Row(
            val id: Long,
            val parentId: Long?,
            val title: String,
            val page: Int,
            val level: Int,
            val offset: Float,
            val scale: Float,
            val translate: Float
        )

        val rows = mutableListOf<Row>()
        while (cursor.moveToNext()) {
            rows.add(
                Row(
                    id = cursor.getLong(0),
                    parentId = if (cursor.isNull(1)) null else cursor.getLong(1),
                    title = cursor.getString(2),
                    page = cursor.getInt(3) - 1,
                    level = cursor.getInt(4),
                    offset = cursor.getFloat(5),
                    scale = cursor.getFloat(6),
                    translate = cursor.getFloat(7)
                )
            )
        }
        cursor.close()
        db.close()
        val childrenMap = rows.groupBy { it.parentId }
        fun build(parentId: Long?): List<TocItem> {
            return childrenMap[parentId]?.map { row ->
                TocItem(
                    tocId = row.id,
                    title = row.title,
                    page = row.page,
                    level = row.level,
                    offset = row.offset,
                    scale = row.scale,
                    translate = row.translate,
                    children = build(row.id)
                )
            } ?: emptyList()
        }
        return build(null)
    }

    private fun toggleAll(items: List<TocItem>, expand: Boolean) {
        items.forEach {
            it.isExpanded = expand
            toggleAll(it.children, expand)
        }
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        view?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    fun setShowingToc(tocShowing: Boolean) {
        toggleVisibleChoices(tocShowing)
    }
}
