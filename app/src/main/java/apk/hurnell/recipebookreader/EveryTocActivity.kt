package apk.hurnell.recipebookreader

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.EveryTocAdapter
import apk.hurnell.recipebookreader.model.TocItem
import androidx.coordinatorlayout.widget.CoordinatorLayout
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import apk.hurnell.recipebookreader.databinding.ActivityEveryTocBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EveryTocTracker(
    val currentCategory: String,
    val searchTerm: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()

class EveryTocActivity : BaseDrawerActivity() {

    private var _binding: ActivityEveryTocBinding? = null
    private val binding get() = _binding!!
    private var searchJob: Job? = null
    private lateinit var adapter: EveryTocAdapter
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private var currentSearchTerm: String = ""
    private var currentCount: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityEveryTocBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadingOverlay = binding.loadingOverlay
        loadingOverlay.visibility = View.GONE
        setupDrawer(binding.everyTocToolbar)
        spinner = binding.categorySpinner
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
        binding.everyTocRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EveryTocAdapter(
            onLongClickTitle = { item ->
                displayClickResult(item.title, binding.rootLayout)
            },
            onClickTitle = { item ->
                if (item.bookLocation != null) {
                    val file = File(item.bookLocation)
                    processAndOpenBook(file, item)
                }
            },
            onClickBook = { item ->
                displayClickResult(item.bookTitle!!, binding.rootLayout)

            },
            onClickHierarchy = { item ->
                displayClickResult(item.hierarchy!!, binding.rootLayout)
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
                        .setTitle("Delete Bookmark?")
                        .setMessage("Are you sure you want to delete the bookmark?")
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
        binding.everyTocRecyclerView.adapter = adapter
        binding.everyTocRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        binding.searchToc.setOnClickListener {
            it.hideKeyboard()
            applyChosenTextAndCategory()
        }
        binding.searchTocEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyChosenTextAndCategory()
                v.hideKeyboard()

                true
            } else {
                false
            }
        }
        addTextWatcher()
        applySavedSettings()

    }

    fun applySavedSettings() {
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            val tracker = dataStoreManager.everyTocState.firstOrNull()
            if (tracker != null) {
                currentCategory = tracker.currentCategory
                currentSearchTerm = tracker.searchTerm
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset
                binding.searchTocEditText.text = Editable.Factory.getInstance().newEditable(currentSearchTerm)

                val position = categories.indexOf(currentCategory)
                spinner.setSelection(position)
                applyChosenTextAndCategory(tracker)
            }
        }
        val position = categories.indexOf("All")
        spinner.setSelection(position)
        applyChosenTextAndCategory(null)

        refreshCategories()
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager = binding.everyTocRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
        val offset = firstVisibleView?.top ?: 0

        lastScrollPosition = firstVisibleItemPosition
        lastScrollOffset = offset
    }

    fun applyChosenTextAndCategory(saved: EveryTocTracker? = null) {
        currentSearchTerm = binding.searchTocEditText.text.toString().trim()
        if (currentSearchTerm != "") {
            val everyToc: List<TocItem> =
                repository.getFilteredEveryToc(currentSearchTerm, currentCategory)
            currentCount = "${everyToc.size}"
            binding.resultCountTextView.text = currentCount
            adapter.submitList(everyToc) {
                if (saved != null) {
                    val layoutManager = binding.everyTocRecyclerView.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(
                        saved.lastScrollPosition,
                        saved.lastScrollOffset
                    )
                }
            }
        } else {
            currentCount = ""
            binding.resultCountTextView.text = currentCount
            adapter.submitList(null)
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
        binding.searchTocEditText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating || s == null) return

                val original = s.toString()
                val filtered = original.lowercase().replace(Regex("[^a-z0-9 ]"), "")

                if (original != filtered) {
                    isUpdating = true
                    val selection = binding.searchTocEditText.selectionStart

                    s.replace(0, s.length, filtered)
                    binding.searchTocEditText.setSelection(selection.coerceAtMost(filtered.length))
                    isUpdating = false
                }

                var bv = View.VISIBLE

                currentSearchTerm = binding.searchTocEditText.text.toString().trim()
                if (binding.searchTocEditText.text.isNullOrEmpty()) {
                    bv = View.INVISIBLE
                    adapter.submitList(null)
                    binding.resultCountTextView.text = ""
                    currentSearchTerm = ""
                }
                binding.searchToc.visibility = bv
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)

                    if (currentSearchTerm.isNotEmpty()) {
                        val count = withContext(Dispatchers.IO) {
                            repository.searchBooks(
                                currentSearchTerm,
                                currentCategory
                            )
                        }
                        currentCount = "$count"
                    } else {
                        currentCount = ""
                    }
                    binding.resultCountTextView.text = currentCount

                }
            }
        })
    }

    fun View.hideKeyboard() {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun refreshFilesAndUI() {}

    override fun onPause() {
        super.onPause()
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@EveryTocActivity::class.java.name)
            val currentTracker = EveryTocTracker(
                currentCategory,
                currentSearchTerm,
                lastScrollPosition,
                lastScrollOffset
            )
            dataStoreManager.saveTracker(DataStoreManager.EVERY_TOC_KEY, currentTracker)
        }
    }
}

