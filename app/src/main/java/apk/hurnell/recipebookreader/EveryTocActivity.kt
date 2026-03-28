package apk.hurnell.recipebookreader

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.EveryTocAdapter
import apk.hurnell.recipebookreader.model.TocItem
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.lifecycleScope
import apk.hurnell.recipebookreader.databinding.ActivityEveryTocBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.annotations.SerializedName

data class EveryTocTracker(
    @SerializedName("currentCategory") val currentCategory: String,
    @SerializedName("searchTerm") val searchTerm: String,
    @SerializedName("lastScrollPosition") val lastScrollPosition: Int,
    @SerializedName("lastScrollOffset") val lastScrollOffset: Int
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
        recipeImagePreviewWrapper = findViewById(R.id.recipeImagePreviewWrapper)
        recipeImagePreview = findViewById(R.id.recipeImagePreview)
        recipeImagePreviewTitle = findViewById(R.id.recipeImagePreviewTitle)
        recipeImageBookTitle = findViewById(R.id.recipeImageBookTitle)
        closePreviewButton = findViewById(R.id.closePreviewButton)
        closePreviewButton?.setOnClickListener { hideRecipeImagePreview() }


        adapter = EveryTocAdapter(
            onLongClickTitle = { item ->
                displaySnackBarMessage(item.title, binding.rootLayout)
            },
            onClickTitle = { item ->
                if (item.bookLocation != null) {
                    val file = File(item.bookLocation)
                    processAndOpenBook(file, item)
                }
            },
            onClickBook = { item ->
                displaySnackBarMessage(item.bookTitle!!, binding.rootLayout)

            },
            onClickHierarchy = { item ->
                displaySnackBarMessage(item.hierarchy!!, binding.rootLayout)
            },
            onClickBookmark = { item ->
                if (item.bookmarkId == null) {
                    val success = repository.createBookmark(item.toBookmarkItem())
                    if (success) {
                        val message =
                            "✅Bookmark with title \"${item.title}\" added to bookmarks"
                        displaySnackBarMessage(message, binding.rootLayout)
                    }
                    applyChosenTextAndCategory()
                } else {
                    val dialog = MaterialAlertDialogBuilder(
                        this,
                        R.style.ThemeOverlay_App_MaterialAlertDialog
                    )
                        .setTitle("Delete Bookmark?")
                        .setMessage("Are you sure you want to delete the bookmark?")
                        .setPositiveButton("Delete") { dialog, _ ->
                            val success = repository.deleteBookmark(item.toBookmarkItem())
                            if (success) {
                                val message = "❌ Bookmark with title \"${item.title}\" deleted"
                                displaySnackBarMessage(message, binding.rootLayout)
                            }
                            this@EveryTocActivity.applyChosenTextAndCategory()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()

                    dialog.window?.setLayout(
                        (resources.displayMetrics.widthPixels * 0.9).toInt(), // 90% of screen width
                        ViewGroup.LayoutParams.WRAP_CONTENT // height wraps content
                    )
                    dialog.window?.setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                    )
                    dialog.window?.setBackgroundDrawableResource(R.drawable.alert_background)
                    dialog.show()
                }
            },
            onClickEye = { item ->
                if (item.bookLocation != null) {
                    val wrapper = recipeImagePreviewWrapper ?: return@EveryTocAdapter
                    handleClickToBuildPageImage(wrapper, item)
                }

            }
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
        lifecycleScope.launch {
            val tracker = dataStoreManager.everyTocState.firstOrNull()
            if (tracker != null) {
                currentCategory = tracker.currentCategory
                currentSearchTerm = tracker.searchTerm
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset
                binding.searchTocEditText.text =
                    Editable.Factory.getInstance().newEditable(currentSearchTerm)

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
        val layoutManager =
            binding.everyTocRecyclerView.layoutManager as? LinearLayoutManager ?: return
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
                    val layoutManager =
                        binding.everyTocRecyclerView.layoutManager as? LinearLayoutManager
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

    fun addTextWatcher() {
        binding.searchTocEditText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating || s == null) return

                val original = s.toString()
                val filtered = original.lowercase().replace(Regex("[^a-z0-9 ]'\""), "")

                if (original != filtered) {
                    isUpdating = true
                    val selection = binding.searchTocEditText.selectionStart

                    s.replace(0, s.length, filtered)
                    binding.searchTocEditText.setSelection(selection.coerceAtMost(filtered.length))
                    isUpdating = false
                }
                adapter.submitList(null)
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

    override fun refreshFilesAndUI(reloadAdapter: Boolean) {}

    private fun saveEveryTocTracker(
        category: String,
        searchTerm: String,
        position: Int,
        offset: Int
    ) {
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@EveryTocActivity::class.java.name)
            val currentTracker = EveryTocTracker(
                category,
                searchTerm,
                position,
                offset
            )
            dataStoreManager.saveTracker(
                DataStoreManager.EVERY_TOC_KEY,
                currentTracker,
                true,
                addToHistory
            )
        }
    }

    override fun onPause() {
        super.onPause()
        saveEveryTocTracker(
            currentCategory,
            currentSearchTerm,
            lastScrollPosition,
            lastScrollOffset
        )
    }
}

