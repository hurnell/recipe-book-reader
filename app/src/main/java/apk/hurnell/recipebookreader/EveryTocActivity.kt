package apk.hurnell.recipebookreader

import android.os.Bundle
import android.view.Gravity
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
import apk.hurnell.recipebookreader.model.TocDisplayItem
import apk.hurnell.recipebookreader.model.TocItem
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.addTextChangedListener
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class EveryTocActivity : BaseDrawerActivity() {

    private lateinit var adapter: EveryTocAdapter
    private lateinit var filterInput: EditText
    private lateinit var resultCountTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_every_toc)
        val rootLayout: CoordinatorLayout = findViewById(R.id.rootLayout)
        repository = PdfRepository(this)
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
                // Handle click on the TOC title
                displayClickResult(item.title, rootLayout)
            },
            onClickTitle = { item ->
                // Handle click on the TOC title
                displayClickResult("${item.title} ${item.page} Should start book: ${item.bookTitle}", rootLayout)
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
            applyChosenTextAndCategory()
        }
        resultCountTextView = findViewById(R.id.resultCountTextView)

        refreshCategories()
    }

    fun applyChosenTextAndCategory() {
        val currentText = filterInput.text.toString()

        if (currentText != "") {
            val everyToc: List<TocItem> = repository.getFilteredEveryToc(currentText, currentCategory)
            resultCountTextView.text = "${everyToc.size}"
            adapter.submitList(everyToc)
        }
    }

    fun displayClickResult(text: String, rootLayout: CoordinatorLayout) {
        val snackBar = Snackbar.make(rootLayout, text, Snackbar.LENGTH_LONG)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 5 // Allow more lines
        snackBar.show()
    }

    fun convertTocItemsToDisplay(list: List<TocItem>): List<TocDisplayItem> {

        // Map for easy parent lookup
        val tocMap = list.associateBy { it.tocId }

        return list.map { item ->
            // Walk parent chain to build hierarchy string
            val parents = mutableListOf<String>()
            var currentParentId = item.parentId?.toLong()
            while (currentParentId != null) {
                val parent = tocMap[currentParentId] ?: break
                parents.add(parent.title)
                currentParentId = parent.parentId?.toLong()
            }

            val hierarchyString = if (parents.isEmpty()) null
            else parents.reversed().joinToString(" -> ")

            TocDisplayItem(
                bookTitle = item.bookTitle,   // <-- include the book name
                title = item.title,
                hierarchy = hierarchyString,
                page = item.page,
                level = item.level,
                tocId = item.tocId
            )
        }
    }

    override fun refreshFilesAndUI() {}
}