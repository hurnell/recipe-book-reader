package apk.hurnell.recipebookreader.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.adapters.TocAdapter
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import apk.hurnell.recipebookreader.model.TocItem

class TocFragment : Fragment() {

    private var bookId: Int = -1
    private var onPageSelected: ((TocItem) -> Unit)? = null
    private var tocData: List<TocItem> = emptyList()
    private var adapter: TocAdapter? = null
    private var allExpanded = false

    companion object {
        private const val ARG_BOOK_ID = "book_id"

        fun newInstance(bookId: Int, listener: (TocItem) -> Unit): TocFragment {
            return TocFragment().apply {
                this.bookId = bookId
                this.onPageSelected = listener
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }

        val btnClear = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            visibility = View.GONE
        }

        val recyclerView = RecyclerView(context).apply {
            id = R.id.recyclerView
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(context)
            clipToPadding = false
        }

        val controlBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor("#F5F5F5".toColorInt())
        }

        val btnToggle = ImageButton(context).apply {
            setImageResource(R.drawable.ic_chevron_right)
            background = null
            setOnClickListener {
                allExpanded = !allExpanded
                toggleAll(tocData, allExpanded)
                adapter?.updateVisibleItems()
                setImageResource(
                    if (allExpanded) R.drawable.ic_expand_more
                    else R.drawable.ic_chevron_right
                )
            }
        }

        val searchField = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            hint = "Search TOC..."
            background = null
            maxLines = 1
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    adapter?.filter(s.toString())
                    recyclerView.scrollToPosition(0)
                    btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        btnClear.setOnClickListener {
            searchField.text.clear()
            hideKeyboard()
            searchField.clearFocus()
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

        adapter = TocAdapter(tocData) { item ->
            onPageSelected?.invoke(item)
            hideKeyboard()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.adapter = adapter

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
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        view?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
