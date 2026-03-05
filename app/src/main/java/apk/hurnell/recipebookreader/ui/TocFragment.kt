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
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.adapters.BookmarkAdapter
import apk.hurnell.recipebookreader.adapters.TocAdapter
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BaseBookmarkTocItem
import apk.hurnell.recipebookreader.model.BookmarkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import apk.hurnell.recipebookreader.model.TocItem
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

class TocFragment : Fragment() {
    private lateinit var tocRecyclerView: RecyclerView
    private lateinit var bookmarkRecyclerView: RecyclerView
    private lateinit var tocSearchBar: LinearLayout
    private lateinit var tocFragmentRootLayout: ConstraintLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var repository: PdfRepository
    private var bookId: Int = -1
    private var onPageSelected: ((BaseBookmarkTocItem) -> Unit)? = null
    private var tocData: List<TocItem> = emptyList()
    private var adapter: TocAdapter? = null
    private var listener: TocFragmentListener? = null


    private var bookmarkData: List<BookmarkItem> = emptyList()
    private var bookmarkAdapter: BookmarkAdapter? = null
    private var allExpanded = false

    companion object {
        fun newInstance(bookId: Int, listener: (BaseBookmarkTocItem) -> Unit): TocFragment {
            return TocFragment().apply {
                this.bookId = bookId
                this.onPageSelected = listener
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is TocFragmentListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement TocFragmentListener")
        }
    }

    private fun toggleVisibleChoices(showToc: Boolean) {

        val tocItem = toolbar.menu.findItem(R.id.action_show_toc)!!
        val bookmarkItem = toolbar.menu.findItem(R.id.action_show_bookmarks)!!
        tocItem.isVisible = !showToc
        bookmarkItem.isVisible = showToc
        tocSearchBar.visibility = if (showToc) View.VISIBLE else View.GONE
        tocRecyclerView.visibility = if (showToc) View.VISIBLE else View.GONE
        bookmarkRecyclerView.visibility = if (showToc) View.GONE else View.VISIBLE
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
        bookmarkRecyclerView = view.findViewById(R.id.bookmarkRecyclerView)
        tocFragmentRootLayout = view.findViewById(R.id.tocFragmentRootLayout)
        tocSearchBar = view.findViewById(R.id.tocSearchBar)
        val searchField = view.findViewById<EditText>(R.id.searchField)
        val btnClear = view.findViewById<ImageButton>(R.id.btnClear)
        val btnToggle = view.findViewById<ImageButton>(R.id.btnToggle)

        tocRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        bookmarkRecyclerView.layoutManager = LinearLayoutManager(requireContext())
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

    private fun reloadAndShowToast(toastText: String) {
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
                if (item.bookmarkId == null) {
                    val success = repository.createBookmark(item.toBookmarkItem())
                    if (success) {
                        val toastText =
                            "✅Bookmark with title ${item.title} for book ${item.bookTitle} to bookmarks"
                        reloadAndShowToast(toastText)
                        loadBookmarksAsync(true)
                    }
                } else {
                    checkDeleteBookmark(item.toBookmarkItem(), true)
                }
            }
        )
        bookmarkAdapter = BookmarkAdapter(
            bookmarkData,
            onClick = { item ->
                onPageSelected?.invoke(item)
                hideKeyboard()
            },
            onDeleteClick = { item ->
                checkDeleteBookmark(item, false)
            },
            onLongClick = { item ->
                displayClickResult(item.title, tocFragmentRootLayout)
            }
        )

        val tocRecyclerView = view.findViewById<RecyclerView>(R.id.tocRecyclerView)
        tocRecyclerView.adapter = adapter
        bookmarkRecyclerView.adapter = bookmarkAdapter
        loadTocAsync()
        loadBookmarksAsync(true)
        toggleVisibleChoices(true)
    }

    fun displayClickResult(text: String, rootLayout: ConstraintLayout) {
        val snackBar = Snackbar.make(rootLayout, text, Snackbar.LENGTH_LONG)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 5
        snackBar.show()
    }

    private fun checkDeleteBookmark(item: BookmarkItem, fromToc: Boolean) {
        val message =
            if (fromToc) "This item is already bookmarked. Do want to delete the bookmark." else "Are you sure you want to delete this bookmark"
        val alertTitle = if (fromToc) "Already Bookmarked" else "Delete Bookmark?"
        AlertDialog.Builder(requireContext())
            .setTitle(alertTitle)
            .setMessage(message)
            .setPositiveButton("Delete") { dialog, _ ->
                val success = repository.deleteBookmark(item)
                if (success) {
                    val toastText = "❌ Bookmark with title ${item.title} deleted"
                    reloadAndShowToast(toastText)
                    loadBookmarksAsync(fromToc)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadBookmarksAsync(fromToc: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                context?.let { repository.getBookmarksForBook(bookId) } ?: emptyList()
            }
            bookmarkData = list
            bookmarkAdapter?.updateData(bookmarkData)
            listener?.onBookmarkDataReloaded(bookmarkData.isEmpty())
            val bookmarkItem = toolbar.menu.findItem(R.id.action_show_bookmarks)!!
            bookmarkItem.isVisible = !bookmarkData.isEmpty()
            if (!fromToc && bookmarkData.isEmpty()) {
                toggleVisibleChoices(true)
            }
        }
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
                    isExpanded = expandedMap[row.id] ?: false
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
