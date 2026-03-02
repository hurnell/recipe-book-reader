package apk.hurnell.recipebookreader

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.BookShelfAdapter
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.FileItem
import java.io.File

data class BookShelfPositionAndCategory(
    val category: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
): BaseTracker()

class BookShelfActivity : BaseDrawerActivity() {
    private lateinit var bookRowAdapter: BookShelfAdapter
    private lateinit var recyclerView: RecyclerView
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private val configurationKey = "BookShelfConfiguration"
    private var justStarted: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_shelf)

        repository = PdfRepository(this)

        loadingOverlay = findViewById(R.id.loadingOverlay)

        val toolbar: Toolbar = findViewById(R.id.bookShelfToolbar)
        spinner = findViewById(R.id.categorySpinner)


        recyclerView = findViewById(R.id.shelfRecyclerView)
        val saved = getSavedParameters()
        currentCategory = saved?.category ?: "All"
        lastScrollPosition = saved?.lastScrollPosition ?: 0
        lastScrollOffset = saved?.lastScrollOffset ?: 0
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!justStarted) {
                    val selectedCategory = parent.getItemAtPosition(position) as String
                    populateShelf(selectedCategory)
                }

                justStarted = false
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                populateShelf("All")
            }
        }

        refreshCategories()
        val position = categories.indexOf(currentCategory)
        spinner.setSelection(position)
        setupDrawer(toolbar)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!justStarted) {
                    trackRecyclerViewOffset()
                }
            }
        })
        bookRowAdapter = BookShelfAdapter(
            onClick = { file -> onBookClicked(file) },
            onLongClick = { file -> shelfShowBookInfoOverlay(file) }
        )
        recyclerView.adapter = bookRowAdapter
        populateShelf(currentCategory, saved == null)
    }

    fun getSavedParameters(): BookShelfPositionAndCategory? {
        val params = repository.getConfiguration(
            configurationKey,
            BookShelfPositionAndCategory::class.java
        )
        return params
    }

    override fun refreshFilesAndUI() {
        populateShelf(currentCategory)
        categories.addAll(repository.getUsedCategories())
        refreshCategories()
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
        val offset = firstVisibleView?.top ?: 0

        lastScrollPosition = firstVisibleItemPosition
        lastScrollOffset = offset
        saveShelfConfiguration()
    }

    private fun saveShelfConfiguration() {
        val configData = BookShelfPositionAndCategory(
            currentCategory,
            lastScrollPosition,
            lastScrollOffset
        )
        if (!justStarted) {
            repository.saveConfiguration(configurationKey, configData)
        }
        justStarted = false
    }

    private fun onBookClicked(file: File) {
        if (file.extension.equals("pdf", ignoreCase = true)) {
            processAndOpenBook(file)
        }
    }

    private fun populateShelf(category: String, acceptZero: Boolean = true) {
        val categoryChanged = currentCategory != category
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


        if (!categoryChanged || !acceptZero) {
            bookRowAdapter.submitList(chunkedList) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(lastScrollPosition, lastScrollOffset)
            }
        } else {
            bookRowAdapter.submitList(chunkedList) {
                recyclerView.scrollToPosition(0)
            }
            lastScrollPosition = 0
            lastScrollOffset = 0
            saveShelfConfiguration()
        }
    }

    private fun shelfShowBookInfoOverlay(pdfFile: File) {
        showBookInfoOverlay(pdfFile)
    }

    private fun getBookShelfBooks(): List<FileItem> {
        return repository.getBookShelfBooks()
    }
}