package apk.hurnell.recipebookreader

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.FileAdapter
import apk.hurnell.recipebookreader.databinding.ActivityBookAuthorOrNameBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.FileItem
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookAuthorOrNameTracker(
    @SerializedName("isAuthorMode") val isAuthorMode: Boolean,
    @SerializedName("searchTerm") val searchTerm: String,
    @SerializedName("lastScrollPosition") val lastScrollPosition: Int,
    @SerializedName("lastScrollOffset") val lastScrollOffset: Int
) : BaseTracker()

class BookAuthorOrNameActivity : BaseDrawerActivity() {

    private var _binding: ActivityBookAuthorOrNameBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileAdapter
    private var isAuthorMode = false
    private var currentBookSearchTerm: String = ""
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private var justStarted = true
    private var suggestJob: Job? = null
    private var ignoreTextChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityBookAuthorOrNameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadingOverlay = binding.loadingOverlay
        loadingOverlay.visibility = View.GONE
        setupDrawer(binding.bookAuthorOrNameToolbar)

        binding.authorOrNameRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.authorOrNameRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })

        adapter = FileAdapter(
            onClick = { file -> processAndOpenBook(file) },
            onLongClick = { file -> showBookInfoOverlay(file) },
            repository = repository,
            pdfOnly = true
        )
        binding.authorOrNameRecyclerView.adapter = adapter

        binding.searchAuthorOrNameEditText.threshold = 1
        binding.searchAuthorOrNameEditText.setOnItemClickListener { parent, _, position, _ ->
            val chosen = parent.getItemAtPosition(position) as String
            ignoreTextChange = true
            binding.searchAuthorOrNameEditText.dismissDropDown()
            binding.searchAuthorOrNameEditText.hideKeyboard()
            performExactSearch(chosen)
        }
        binding.searchAuthorOrNameEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                v.hideKeyboard()
                performExactSearch(binding.searchAuthorOrNameEditText.text.toString())
                true
            } else {
                false
            }
        }
        addTextWatcher()
        binding.btnToggleSearchMode.setOnClickListener {
            isAuthorMode = !isAuthorMode
            updateSearchModeUi()
            clearSearchAndResults()
        }

        applySavedSettings()
    }

    private fun updateSearchModeUi() {
        if (isAuthorMode) {
            binding.btnToggleSearchMode.setImageResource(R.drawable.ic_person_author)
            binding.btnToggleSearchMode.contentDescription =
                getString(R.string.toggle_search_mode_to_title)
            binding.searchAuthorOrNameInputLayout.hint = getString(R.string.hint_search_by_author)
        } else {
            binding.btnToggleSearchMode.setImageResource(R.drawable.ic_book_title)
            binding.btnToggleSearchMode.contentDescription =
                getString(R.string.toggle_search_mode_to_author)
            binding.searchAuthorOrNameInputLayout.hint = getString(R.string.hint_search_by_title)
        }
    }

    private fun clearSearchAndResults() {
        suggestJob?.cancel()
        ignoreTextChange = true
        binding.searchAuthorOrNameEditText.setText("")
        binding.searchAuthorOrNameEditText.setAdapter(null)
        currentBookSearchTerm = ""
        adapter.submitList(emptyList())
    }

    private fun noFilterAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            private val passThroughFilter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    return FilterResults().apply {
                        values = items
                        count = items.size
                    }
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }

            override fun getFilter(): Filter = passThroughFilter
        }
    }

    private fun addTextWatcher() {
        binding.searchAuthorOrNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (ignoreTextChange) {
                    ignoreTextChange = false
                    return
                }
                val term = s?.toString()?.trim().orEmpty()
                suggestJob?.cancel()
                if (term.isEmpty()) {
                    binding.searchAuthorOrNameEditText.setAdapter(null)
                    currentBookSearchTerm = ""
                    adapter.submitList(emptyList())
                    return
                }
                suggestJob = lifecycleScope.launch {
                    delay(300)
                    val suggestions = withContext(Dispatchers.IO) {
                        if (isAuthorMode) {
                            repository.getDistinctBookAuthors(term)
                        } else {
                            repository.getDistinctBookNames(term)
                        }
                    }
                    binding.searchAuthorOrNameEditText.setAdapter(noFilterAdapter(suggestions))
                    if (binding.searchAuthorOrNameEditText.isFocused && suggestions.isNotEmpty()) {
                        binding.searchAuthorOrNameEditText.showDropDown()
                    }
                }
            }
        })
    }

    private fun performExactSearch(term: String) {
        currentBookSearchTerm = term.trim()
        if (currentBookSearchTerm.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                if (isAuthorMode) {
                    repository.getBooksByExactAuthor(currentBookSearchTerm)
                } else {
                    repository.getBooksByExactName(currentBookSearchTerm)
                }
            }
            adapter.submitList(results)
        }
    }

    private fun View.hideKeyboard() {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    fun applySavedSettings() {
        lifecycleScope.launch {
            val tracker = dataStoreManager.bookAuthorOrNameState.firstOrNull()
            if (tracker != null) {
                isAuthorMode = tracker.isAuthorMode
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset
                updateSearchModeUi()

                ignoreTextChange = true
                binding.searchAuthorOrNameEditText.setText(tracker.searchTerm)

                if (tracker.searchTerm.isNotEmpty()) {
                    currentBookSearchTerm = tracker.searchTerm
                    val results = withContext(Dispatchers.IO) {
                        if (isAuthorMode) {
                            repository.getBooksByExactAuthor(tracker.searchTerm)
                        } else {
                            repository.getBooksByExactName(tracker.searchTerm)
                        }
                    }
                    adapter.submitList(results) {
                        (binding.authorOrNameRecyclerView.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(lastScrollPosition, lastScrollOffset)
                        binding.authorOrNameRecyclerView.post { justStarted = false }
                    }
                } else {
                    justStarted = false
                }
            } else {
                updateSearchModeUi()
                justStarted = false
            }
        }
    }

    private fun trackRecyclerViewOffset() {
        if (!justStarted) {
            val layoutManager =
                binding.authorOrNameRecyclerView.layoutManager as? LinearLayoutManager ?: return
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
            val offset = firstVisibleView?.top ?: 0
            lastScrollPosition = firstVisibleItemPosition
            lastScrollOffset = offset
        }
    }

    override fun refreshFilesAndUI(reloadAdapter: Boolean) {}

    override fun onResume() {
        super.onResume()
        if (findViewById<DrawerLayout>(R.id.drawer_layout) != null) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }
    }

    private fun saveBookAuthorOrNameTracker(
        authorMode: Boolean,
        searchTerm: String,
        position: Int,
        offset: Int
    ) {
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@BookAuthorOrNameActivity::class.java.name)
            val currentTracker = BookAuthorOrNameTracker(
                authorMode,
                searchTerm,
                position,
                offset
            )
            dataStoreManager.saveTracker(
                DataStoreManager.BOOK_AUTHOR_OR_NAME_KEY,
                currentTracker,
                true,
                addToHistory
            )
        }
    }

    override fun onPause() {
        binding.authorOrNameRecyclerView.clearOnScrollListeners()
        suggestJob?.cancel()
        super.onPause()
        saveBookAuthorOrNameTracker(
            isAuthorMode,
            currentBookSearchTerm,
            lastScrollPosition,
            lastScrollOffset
        )
    }
}
