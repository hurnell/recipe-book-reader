package apk.hurnell.recipebookreader

import android.R.attr.delay
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.EveryTocAdapter
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.TocItem
import androidx.coordinatorlayout.widget.CoordinatorLayout
import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EveryTocPositionAndSearchTerm(
    val currentCategory: String,
    val searchTerm: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) {
    fun asString():String{
        return "$currentCategory $searchTerm $lastScrollPosition $lastScrollOffset"
    }
}

class EveryTocActivity : BaseDrawerActivity() {
    private var searchJob: Job? = null
    private lateinit var adapter: EveryTocAdapter
    private lateinit var filterInput: EditText
    private lateinit var resultCountTextView: TextView
    private lateinit var clearSearch: ImageButton
    private lateinit var searchToc: ImageButton
    private lateinit var recyclerView: RecyclerView
    private val configurationKey = "EveryTocConfiguration"
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private var justStarted: Boolean = true
    private var currentSearchTerm: String = ""
    private var currentCount: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_every_toc)
        val rootLayout: CoordinatorLayout = findViewById(R.id.rootLayout)
        repository = PdfRepository(this)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        val toolbar: Toolbar = findViewById(R.id.everyTocToolbar)
        setupDrawer(toolbar)
        spinner = findViewById(R.id.categorySpinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentCategory = parent.getItemAtPosition(position) as String
                applyChosenTextAndCategory()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                currentCategory = "All"
                applyChosenTextAndCategory()
            }
        }
        recyclerView = findViewById(R.id.everyTocRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EveryTocAdapter(
            onLongClickTitle = { item ->
                displayClickResult(item.title, rootLayout)
            },
            onClickTitle = { item ->
                if (item.bookLocation != null) {
                    val file = File(item.bookLocation)
                    processAndOpenBook(file, item)
                }
            },
            onClickBook = { item ->
                displayClickResult(item.bookTitle!!, rootLayout)

            },
            onClickHierarchy = { item ->
                displayClickResult(item.hierarchy!!, rootLayout)
            },
            onClickBookmark = { item ->
                if (item.bookmarkId == null) {
                    val success = repository.createBookmark(item.toBookmarkItem())
                    if (success) {
                        val toastText =
                            "✅Bookmark with title \"${item.title}\" for book \"${item.bookTitle}\" to bookmarks"
                        Toast.makeText(
                            this,
                            toastText,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    applyChosenTextAndCategory()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Already Bookmarked")
                        .setMessage("This item is already bookmarked do want to delete the bookmark.")
                        .setPositiveButton("Delete") { dialog, _ ->
                            val success = repository.deleteBookmark(item.toBookmarkItem())
                            if (success) {
                                val toastText = "❌ Bookmark with title \"${item.title}\" deleted"
                                Toast.makeText(
                                    this@EveryTocActivity,
                                    toastText,
                                    Toast.LENGTH_SHORT
                                ).show()

                            }
                            this@EveryTocActivity.applyChosenTextAndCategory()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            },
        )
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!justStarted) {
                    trackRecyclerViewOffset()
                }
                justStarted = false
            }
        })
        filterInput = findViewById(R.id.filterInput)

        searchToc = findViewById(R.id.searchToc)
        searchToc.setOnClickListener {
            it.hideKeyboard()
            applyChosenTextAndCategory()
        }
        filterInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyChosenTextAndCategory()
                v.hideKeyboard()

                true
            } else {
                false
            }
        }
        resultCountTextView = findViewById(R.id.resultCountTextView)
        clearSearch = findViewById(R.id.clearSearch)
        addTextWatcher()
        clearSearch.setOnClickListener {
            it.hideKeyboard()
            filterInput.text.clear()
            clearSearch.visibility = View.GONE
            searchToc.visibility = View.GONE
            currentCount = ""
            resultCountTextView.text = currentCount
            adapter.submitList(null)
        }
        val saved = getSavedParameters()
        currentCategory = saved?.currentCategory ?: "All"
        currentSearchTerm = saved?.searchTerm ?: ""
        lastScrollPosition = saved?.lastScrollPosition ?: 0
        lastScrollOffset = saved?.lastScrollOffset ?: 0
        filterInput.text = Editable.Factory.getInstance().newEditable(currentSearchTerm)
        val position = categories.indexOf(currentCategory)
        spinner.setSelection(position)
        applyChosenTextAndCategory(saved)

        refreshCategories()
    }

    fun getSavedParameters(): EveryTocPositionAndSearchTerm? {
        val params = repository.getConfiguration(
            configurationKey,
            EveryTocPositionAndSearchTerm::class.java
        )
        return params
    }


    private fun saveEveryTocConfiguration() {
        val configData = EveryTocPositionAndSearchTerm(
            currentCategory,
            currentSearchTerm,
            lastScrollPosition,
            lastScrollOffset
        )
        if (!justStarted) {
            Log.i(
                "NIGEL_HURNELL",
                "configData = ${configData.asString()} "
            )
            repository.saveConfiguration(configurationKey, configData)
        }
        justStarted = false
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
        val offset = firstVisibleView?.top ?: 0

        lastScrollPosition = firstVisibleItemPosition
        lastScrollOffset = offset
        if (!justStarted) {
            saveEveryTocConfiguration()
        }
        justStarted = false
    }

    fun applyChosenTextAndCategory(saved: EveryTocPositionAndSearchTerm? = null) {
        currentSearchTerm = filterInput.text.toString().trim()
        if (currentSearchTerm != "") {
            val everyToc: List<TocItem> =
                repository.getFilteredEveryToc(currentSearchTerm, currentCategory)
            currentCount = "${everyToc.size}"
            resultCountTextView.text = currentCount
            adapter.submitList(everyToc){
                if (saved != null) {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(saved.lastScrollPosition, saved.lastScrollOffset)
                }
            }
        } else {
            currentCount = ""
            resultCountTextView.text = currentCount
            adapter.submitList(null)
        }
        if (!justStarted) {
            saveEveryTocConfiguration()
        }
    }

    fun displayClickResult(text: String, rootLayout: CoordinatorLayout) {
        val snackBar = Snackbar.make(rootLayout, text, Snackbar.LENGTH_LONG)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 5
        snackBar.show()
    }

    fun addTextWatcher() {
        filterInput.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating || s == null) return

                val original = s.toString()
                val filtered = original.lowercase().replace(Regex("[^a-z0-9 ]"), "")

                if (original != filtered) {
                    isUpdating = true
                    val selection = filterInput.selectionStart

                    s.replace(0, s.length, filtered)
                    filterInput.setSelection(selection.coerceAtMost(filtered.length))
                    isUpdating = false
                }
                val bv = if (filterInput.text.toString().isEmpty()) View.INVISIBLE else View.VISIBLE
                clearSearch.visibility = bv
                searchToc.visibility = bv
                searchJob?.cancel() // Cancel the previous search if user typed again
                searchJob = lifecycleScope.launch {
                    delay(300)
                    currentSearchTerm = filterInput.text.toString().trim()

                    if (currentSearchTerm.isNotEmpty()) {
                        val count = withContext(Dispatchers.IO) {
                            repository.searchBooks(
                                currentSearchTerm,
                                currentCategory
                            ) // Your DB call here
                        }
                        currentCount = "$count"
                    }
                    resultCountTextView.text = currentCount

                }
            }
        })
    }

    fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun refreshFilesAndUI() {}
}

