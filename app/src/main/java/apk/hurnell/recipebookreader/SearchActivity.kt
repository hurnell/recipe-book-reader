package apk.hurnell.recipebookreader

import android.app.appsearch.SearchResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import apk.hurnell.recipebookreader.adapters.SearchAdapter
import apk.hurnell.recipebookreader.databinding.ActivitySearchBinding
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.helpers.PdfSearchHelper
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var searchHelper: PdfSearchHelper
    private var searchJob: Job? = null
    private lateinit var adapter: SearchAdapter
    private lateinit var repository: PdfRepository
    private var document: Document? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val pdfFilePath = intent.getStringExtra("PDF_PATH")
        if (pdfFilePath == null) {
            Log.e("NIGEL_HURNELL", "No PDF path provided")
            finish()
            return
        }
        val pdfFile = File(pdfFilePath)
        repository = PdfRepository(this)
        lifecycleScope.launch {
            try {
                val currentDocument = runCatching {
                    repository.openPdfFast(pdfFile)
                }.getOrElse {
                    Log.e("NIGEL_HURNELL", "Failed to open document", it)
                    finish()
                    return@launch
                }
                document = currentDocument

                searchHelper = PdfSearchHelper(currentDocument)
            } catch (e: Exception) {
                Log.e("NIGEL_HURNELL", "Error loading PDF", e)
                finish()
            }
        }

        setupSearchTriggers()
        setupToolbar()
        setupRecyclerView()
    }
    private fun setupRecyclerView() {
        adapter = SearchAdapter { result ->
            val data = Intent()
            data.putExtra("SELECTED_PAGE", result.pageIndex)
            val gson = com.google.gson.Gson()
            val rectanglesJson = gson.toJson(result.blockRects)
            data.putExtra("BLOCK_RECTANGLES_JSON", rectanglesJson)
            val pageCoordinatesJson = gson.toJson(result.coordinates)
            data.putExtra("PAGE_COORDINATES", pageCoordinatesJson)
            setResult(RESULT_OK, data)
            finish()
        }
        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchResultsRecyclerView.adapter = adapter
    }
    private fun setupSearchTriggers() {
        binding.btnDoSearch.setOnClickListener {
            val query = binding.searchEditText.text.toString()
            if (query.isNotBlank()) {
                startSearch(query)
                hideKeyboard()
            }
        }
        binding.searchEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch(v.text.toString())
                hideKeyboard()
                true
            } else false
        }
        binding.searchInputLayout.setEndIconOnClickListener {
            binding.searchEditText.text?.clear()
            searchJob?.cancel()
            adapter.clear()
            binding.searchProgressBar.visibility = View.GONE
            hideKeyboard()
        }
        binding.searchEditText.addTextChangedListener { text ->
            if (text.isNullOrEmpty()) {
                searchJob?.cancel()
                adapter.clear()
                binding.searchProgressBar.visibility = View.GONE
                binding.btnDoSearch.visibility = View.INVISIBLE
            } else {
                binding.btnDoSearch.visibility = View.VISIBLE
            }
        }
    }
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun setupToolbar() {
        // Back button in toolbar
        binding.searchToolbar.setNavigationOnClickListener { finish() }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch(binding.searchEditText.text.toString())
                true
            } else false
        }
    }
    private fun startSearch(query: String) {
        searchJob?.cancel()
        adapter.clear()
        binding.searchProgressBar.visibility = View.VISIBLE

        searchJob = lifecycleScope.launch {
            searchHelper.search(query).collect { result ->
                adapter.addResult(result)
            }
            binding.searchProgressBar.visibility = View.GONE
            if (adapter.itemCount == 0) {
                // Toast or View to show "No results found"
            }
        }
    }

    private suspend fun searchThroughPages(query: String): List<SearchResult> {
        // Here you would call your mCore.searchPage(index, query)
        // because this is in Dispatchers.Default, it won't freeze the UI
        return listOf()
    }
}