package apk.hurnell.recipebookreader.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.google.android.material.snackbar.Snackbar
import apk.hurnell.recipebookreader.databinding.FragmentTocBinding

class TocFragment : Fragment() {
    private var _binding: FragmentTocBinding? = null
    private val binding get() = _binding!!
    private lateinit var tocFragmentRootLayout: ConstraintLayout
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
    object TocMenuIds {
        var SHOW_TOC = R.id.action_show_toc
        var SHOW_BOOKMARKS = R.id.action_show_bookmarks
    }

    private fun toggleVisibleChoices(showToc: Boolean) {

        val tocItem = binding.tocToolbar.menu.findItem(TocMenuIds.SHOW_TOC)
        val bookmarkItem = binding.tocToolbar.menu.findItem(TocMenuIds.SHOW_BOOKMARKS)
        tocItem.isVisible = !showToc
        bookmarkItem.isVisible = showToc && !bookmarkData.isEmpty()
        binding.tocSearchBar.visibility = if (showToc) View.VISIBLE else View.GONE
        binding.tocRecyclerView.visibility = if (showToc) View.VISIBLE else View.GONE
        binding.bookmarkRecyclerView.visibility = if (showToc) View.GONE else View.VISIBLE
        binding.tocToolbar.title = if (showToc) "TOC" else "Bookmarks"
    }

    private fun setupToolbar(view: View) {
        binding.tocToolbar.inflateMenu(R.menu.toc_toolbar_menu)
        binding.tocToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.tocToolbar.setOnMenuItemClickListener { item ->
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
        _binding = FragmentTocBinding.inflate(inflater, container, false)
        setupToolbar(binding.root)

        binding.tocRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.bookmarkRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.btnToggle.setOnClickListener {
            allExpanded = !allExpanded
            toggleAll(tocData, allExpanded)
            adapter?.updateVisibleItems()
            binding.btnToggle.setImageResource(
                if (allExpanded) R.drawable.ic_expand_more
                else R.drawable.ic_chevron_right
            )
        }
        binding.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter?.filter(s.toString())
                binding.tocRecyclerView.scrollToPosition(0)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        return binding.root
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
                        val message =
                            "✅Bookmark with title ${item.title} for book ${item.bookTitle} to bookmarks"
                        displaySnackBarMessage(message, tocFragmentRootLayout)

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
                displaySnackBarMessage(item.title, tocFragmentRootLayout)
            }
        )

        binding.tocRecyclerView.adapter = adapter
        binding.bookmarkRecyclerView.adapter = bookmarkAdapter
        loadTocAsync()
        loadBookmarksAsync(true)
        toggleVisibleChoices(true)
    }

    fun displaySnackBarMessage(text: String, rootLayout: ConstraintLayout) {
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
        AlertDialog.Builder(requireContext(),R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(alertTitle)
            .setMessage(message)
            .setPositiveButton("Delete") { dialog, _ ->
                val success = repository.deleteBookmark(item)
                if (success) {
                    val message = "❌ Bookmark with title ${item.title} deleted"
                    displaySnackBarMessage(message, tocFragmentRootLayout)
                    loadBookmarksAsync(fromToc)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun loadBookmarksAsync(fromToc: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                context?.let { repository.getBookmarksForBook(bookId) } ?: emptyList()
            }
            bookmarkData = list
            bookmarkAdapter?.updateData(bookmarkData)
            listener?.onBookmarkDataReloaded(bookmarkData.isEmpty())
            val bookmarkItem = binding.tocToolbar.menu.findItem(R.id.action_show_bookmarks)!!
            bookmarkItem.isVisible = bookmarkData.isNotEmpty()
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
                    parentId = row.parentId?.toInt(),
                    title = row.title,
                    bookmarkId = row.bookmarkId,
                    page = row.page,
                    level = row.level,
                    scale = row.scale,
                    translate = row.translate,
                    hierarchy = row.parentTitle,
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
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
