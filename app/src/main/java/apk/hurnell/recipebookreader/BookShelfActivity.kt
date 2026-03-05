package apk.hurnell.recipebookreader

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.BookShelfAdapter
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.FileItem
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

data class BookShelfTracker(
    val category: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()

class BookShelfActivity : BaseDrawerActivity() {
    private lateinit var bookRowAdapter: BookShelfAdapter
    private lateinit var recyclerView: RecyclerView
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_shelf)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingOverlay.visibility = View.GONE
        val toolbar: Toolbar = findViewById(R.id.bookShelfToolbar)
        spinner = findViewById(R.id.categorySpinner)


        recyclerView = findViewById(R.id.shelfRecyclerView)
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

        setupDrawer(toolbar)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        bookRowAdapter = BookShelfAdapter(
            onClick = { file -> onBookClicked(file) },
            onLongClick = { file -> shelfShowBookInfoOverlay(file) }
        )
        recyclerView.adapter = bookRowAdapter

        applySavedSettings()

    }

    private fun updateFromSavedSettings(currentCategory: String, acceptZero: Boolean = true){
        refreshCategories()
        val position = categories.indexOf(currentCategory)
        spinner.setSelection(position)
        populateShelf(currentCategory, acceptZero)
    }

    fun applySavedSettings() {
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            val tracker = dataStoreManager.bookShelfState.firstOrNull()
            if (tracker != null) {
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset
                currentCategory = tracker.category
                updateFromSavedSettings(currentCategory, false)
            } else {
                updateFromSavedSettings("All")
            }
        }
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
        }
    }

    private fun shelfShowBookInfoOverlay(pdfFile: File) {
        showBookInfoOverlay(pdfFile)
    }

    private fun getBookShelfBooks(): List<FileItem> {
        return repository.getBookShelfBooks()
    }

    override fun onPause() {
        super.onPause()
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@BookShelfActivity::class.java.name)
            val currentTracker = BookShelfTracker(
                currentCategory,
                lastScrollPosition,
                lastScrollOffset
            )
            dataStoreManager.saveTracker(DataStoreManager.BOOK_SHELF_KEY, currentTracker)
        }
    }
}