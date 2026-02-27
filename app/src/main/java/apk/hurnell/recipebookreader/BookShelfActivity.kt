package apk.hurnell.recipebookreader

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.BookShelfAdapter
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.FileItem
import java.io.File

class BookShelfActivity : BaseDrawerActivity() {
    private lateinit var bookRowAdapter: BookShelfAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_shelf)

        repository = PdfRepository(this)

        loadingOverlay = findViewById(R.id.loadingOverlay)

        val toolbar: Toolbar = findViewById(R.id.bookShelfToolbar)
        spinner = findViewById(R.id.categorySpinner)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedCategory = parent.getItemAtPosition(position) as String
                populateShelf(selectedCategory)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                populateShelf("All")
            }
        }

        refreshCategories()
        setupDrawer(toolbar)

        recyclerView = findViewById<RecyclerView>(R.id.shelfRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null
        bookRowAdapter = BookShelfAdapter(
            onClick = { file -> onBookClicked(file) },
            onLongClick = { file -> shelfShowBookInfoOverlay(file) }
        )
        recyclerView.adapter = bookRowAdapter

        populateShelf("All")
    }

    override fun refreshFilesAndUI() {
        populateShelf(currentCategory)
        categories.addAll(repository.getUsedCategories())
        refreshCategories()
    }

    private fun onBookClicked(file: File) {
        if (file.extension.equals("pdf", ignoreCase = true)) {
            processAndOpenBook(file)
        }
    }

    private fun populateShelf(category: String) {
        currentCategory = category
        val allBooks: List<FileItem> = getBookShelfBooks()

        val filteredBooks = if (category == "All") {
            allBooks
        } else {
            allBooks.filter {
                it.bookInfo?.mainCategory == category ||
                        it.bookInfo?.subCategory == category
            }
        }.sortedWith(compareBy<FileItem> {
            // Items without a subcategory go last
            it.bookInfo?.subCategory.isNullOrEmpty()
        }.thenBy {
            // Items with subcategory are sorted alphabetically
            it.bookInfo?.subCategory ?: ""
        }.thenBy {
            // If subcategory is same, sort by main category
            it.bookInfo?.mainCategory ?: ""
        })
        filteredBooks.forEach { item ->
            val main = item.bookInfo?.mainCategory ?: "null"
            val sub = item.bookInfo?.subCategory ?: "null"
            Log.i("BookShelfSort", "Book: , MainCategory: $main, SubCategory: $sub")
        }
        val minSlots = filteredBooks.size.coerceAtLeast(15)

        // Round up to nearest multiple of 3
        val totalSlotsNeeded = if (minSlots % 3 == 0) {
            minSlots
        } else {
            minSlots + (3 - (minSlots % 3))
        }

        val displayList = filteredBooks.toMutableList<FileItem?>()

        repeat(totalSlotsNeeded - displayList.size) {
            displayList.add(null)
        }
        val chunkedList = displayList.chunked(3)

        bookRowAdapter.submitList(chunkedList) {
            recyclerView.scrollToPosition(0)
        }

    }

    private fun shelfShowBookInfoOverlay(pdfFile: File) {
        showBookInfoOverlay(pdfFile)
    }

    private fun getBookShelfBooks(): List<FileItem> {
        return repository.getBookShelfBooks()
    }
}