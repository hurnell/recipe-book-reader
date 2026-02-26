package apk.hurnell.recipebookreader

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
import android.view.inputmethod.InputMethodManager
import com.google.android.material.snackbar.Snackbar
import java.io.File

class EveryTocActivity : BaseDrawerActivity() {

    private lateinit var adapter: EveryTocAdapter
    private lateinit var filterInput: EditText
    private lateinit var resultCountTextView: TextView
    private lateinit var clearSearch: ImageButton

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
        val recyclerView: RecyclerView = findViewById(R.id.everyTocRecyclerView)
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
            }
        )
        recyclerView.adapter = adapter

        filterInput = findViewById(R.id.filterInput)

        val searchToc: ImageButton = findViewById(R.id.searchToc)
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
            resultCountTextView.text = ""
            adapter.submitList(null)
        }

        refreshCategories()
    }

    fun applyChosenTextAndCategory() {
        val currentText = filterInput.text.toString()

        if (currentText != "") {
            val everyToc: List<TocItem> =
                repository.getFilteredEveryToc(currentText.trim(), currentCategory)
            resultCountTextView.text = "${everyToc.size}"
            adapter.submitList(everyToc)
        } else {
            resultCountTextView.text = ""
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
                clearSearch.visibility =
                    if (filterInput.text.toString().isEmpty()) View.GONE else View.VISIBLE
            }
        })
    }

    fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun refreshFilesAndUI() {}
}

