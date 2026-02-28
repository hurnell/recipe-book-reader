package apk.hurnell.recipebookreader.ui

import android.app.AlertDialog
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
import apk.hurnell.recipebookreader.helpers.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import apk.hurnell.recipebookreader.model.TocItem
import com.google.android.material.appbar.MaterialToolbar

class TocFragment : Fragment() {
    private lateinit var tocRecyclerView: RecyclerView
    private lateinit var tocSearchBar: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var repository: PdfRepository
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

    private fun getExpandedStateMap(items: List<TocItem>): Map<Long, Boolean> {
        val map = mutableMapOf<Long, Boolean>()
        fun traverse(itemList: List<TocItem>) {
            for (item in itemList) {
                map[item.tocId] = item.isExpanded
                traverse(item.children)
            }
        }
        traverse(items)
        return map
    }

    private fun reloadAndShowToast(toastText: String){
        loadTocAsync()
        Toast.makeText(
            context,
            toastText,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = PdfRepository(requireContext())
        adapter = TocAdapter(
            tocData,
            onClick = { item ->
                onPageSelected?.invoke(item)
                hideKeyboard()
            },
            onLongClick = { item ->
                var createDeleteSuccess = false
                if (item.bookmarkId == null) {
                    createDeleteSuccess = repository.createBookmark(item.toBookmarkItem())
                    if (createDeleteSuccess) {
                        val toastText =
                            "✅Bookmark with title ${item.title} for book ${item.bookTitle} to bookmarks"
                        reloadAndShowToast(toastText)
                    }
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Already Bookmarked")
                        .setMessage("This item is already bookmarked do want to delete the bookmark.")
                        .setPositiveButton("Delete") { dialog, _ ->
                            createDeleteSuccess = repository.deleteBookmark(item.toBookmarkItem())
                            if (createDeleteSuccess) {
                                val toastText =  "❌ Bookmark with title ${item.title} deleted"
                                reloadAndShowToast(toastText)
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        )

        val tocRecyclerView = view.findViewById<RecyclerView>(R.id.tocRecyclerView)
        tocRecyclerView.adapter = adapter

        loadTocAsync()
    }

    private fun loadTocAsync() {
        viewLifecycleOwner.lifecycleScope.launch {
            val expandedMap = getExpandedStateMap(tocData)
            val list = withContext(Dispatchers.IO) {
                context?.let { loadTocFromDatabase(bookId, expandedMap) } ?: emptyList()
            }
            tocData = list
            adapter?.updateData(tocData)
        }
    }

    private fun loadTocFromDatabase(
        bookId: Int,
        expandedMap: Map<Long, Boolean> = emptyMap()
    ): List<TocItem> {
        val rows = repository.getTocRows(bookId)
        val childrenMap = rows.groupBy { it.parentId }

        fun build(parentId: Long?): List<TocItem> {
            return childrenMap[parentId]?.map { row ->
                TocItem(
                    tocId = row.id,
                    bookId = row.bookId,
                    bookTitle = row.bookTitle,
                    title = row.title,
                    bookmarkId = row.bookmarkId,
                    page = row.page,
                    level = row.level,
                    offset = row.offset,
                    scale = row.scale,
                    translate = row.translate,
                    children = build(row.id),
                    isExpanded = expandedMap[row.id] ?: false  // Restore state here
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
