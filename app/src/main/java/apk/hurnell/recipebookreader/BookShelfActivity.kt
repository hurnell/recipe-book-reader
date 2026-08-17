package apk.hurnell.recipebookreader

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.BookShelfAdapter
import apk.hurnell.recipebookreader.databinding.ActivityBookShelfBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.CategoryItem
import apk.hurnell.recipebookreader.model.FileItem
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

data class BookShelfTracker(
    @SerializedName("category") val category: String,
    @SerializedName("lastScrollPosition") val lastScrollPosition: Int,
    @SerializedName("lastScrollOffset") val lastScrollOffset: Int
) : BaseTracker()

class BookShelfActivity : BaseDrawerActivity() {

    private var _binding: ActivityBookShelfBinding? = null
    private val binding get() = _binding!!
    private lateinit var bookRowAdapter: BookShelfAdapter
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityBookShelfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadingOverlay = binding.loadingOverlay
        loadingOverlay.visibility = View.GONE
        spinner = binding.categorySpinner

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val item = parent.getItemAtPosition(position) as CategoryItem
                val selectedCategory = item.category
                populateShelf(selectedCategory)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                populateShelf(currentCategory)
            }
        }

        setupDrawer(binding.bookShelfToolbar)
        binding.shelfRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.shelfRecyclerView.itemAnimator = null
        binding.shelfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        bookRowAdapter = BookShelfAdapter(
            onClick = { file -> onBookClicked(file) },
            onLongClick = { file -> shelfShowBookInfoOverlay(file) }
        )
        binding.shelfRecyclerView.adapter = bookRowAdapter

        applySavedSettings()

    }

    private fun updateFromSavedSettings(currentCategory: String, acceptZero: Boolean = true) {
        refreshCategories()
        val position = categories.indexOf(currentCategory)
        spinner.setSelection(position)
        populateShelf(currentCategory, acceptZero, true)
    }

    fun applySavedSettings() {
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

    override fun refreshFilesAndUI(reloadAdapter: Boolean) {
        populateShelf(currentCategory)
        val position = categories.indexOf(currentCategory)
        spinner.setSelection(position)
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager =
            binding.shelfRecyclerView.layoutManager as? LinearLayoutManager ?: return
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

    private fun populateShelf(
        category: String,
        acceptZero: Boolean = true,
        fromSaved: Boolean = false
    ) {
        val categoryChanged = currentCategory != category
        currentCategory = category
        val allBooks: List<FileItem> = getBookShelfBooks(category)

        var filteredBooks = if (category == "All") {
            allBooks
        } else {
            allBooks.filter {
                it.bookInfo?.mainCategory == category ||
                        it.bookInfo?.subCategories?.contains(category) == true
            }
        }
        filteredBooks = if (category in listOf("All", "Cookbooks")) {
            filteredBooks.sortedWith(compareBy<FileItem> {
                //Log.e("NIGEL_HURNELL", it.bookInfo?.subCategories.toString())
                it.bookInfo?.subCategories.isNullOrEmpty()
            }.thenBy {
                it.bookInfo?.subCategories?.minOrNull() ?: ""
            }.thenBy {
                it.bookInfo?.mainCategory ?: ""
            })
        }else {
            filteredBooks.sortedWith(compareBy<FileItem> {
                it.bookInfo?.author.isNullOrEmpty()
            }.thenBy {
                it.bookInfo?.author ?: ""
            }.thenBy {
                it.bookInfo?.name.isNullOrEmpty()
            }.thenBy {
                it.bookInfo?.name ?: ""
            })
        }

        val minSlots = filteredBooks.size.coerceAtLeast(15)
        if (!fromSaved) {
            var mainCategory = filteredBooks
                .map { it.bookInfo?.mainCategory }
                .distinct()
                .singleOrNull()
            val bookMessage = if (filteredBooks.size == 1 && mainCategory != "Cookbooks") {
                " book"
            } else if (mainCategory != "Cookbooks") {
                " books"
            } else {
                ""
            }
            if (mainCategory == "Cookbooks" && filteredBooks.size == 1) {
                mainCategory = "Cookbook"
            }
            if (mainCategory == null) {
                mainCategory = ""
            }
            val forMessage = when (category) {
                "All" -> {
                    ""
                }
                "Cookbooks" if (mainCategory == "Cookbook" || mainCategory == "Cookbooks") -> {
                    ""
                }
                mainCategory -> {
                    ""
                }
                else -> {
                    " $category"
                }
            }
            val message = "Showing ${filteredBooks.size}$forMessage $mainCategory$bookMessage"
            displaySnackBarMessage(message, binding.rootLayout)
        }
        val totalSlotsNeeded = if (minSlots % 3 == 0) {
            minSlots
        } else {
            minSlots + (3 - (minSlots % 3))
        }

        val displayList = filteredBooks.toMutableList<FileItem?>()
        Log.e("NIGEL_HURNELL", displayList.toString())
        repeat(totalSlotsNeeded - displayList.size) {
            displayList.add(null)
        }
        val chunkedList = displayList.chunked(3)


        if (!categoryChanged || !acceptZero) {
            bookRowAdapter.submitList(chunkedList) {
                val layoutManager = binding.shelfRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(lastScrollPosition, lastScrollOffset)
            }
        } else {
            bookRowAdapter.submitList(chunkedList) {
                binding.shelfRecyclerView.scrollToPosition(0)
            }
            lastScrollPosition = 0
            lastScrollOffset = 0
        }

    }


    private fun saveBookShelfTracker(
        category: String,
        position: Int,
        offset: Int
    ) {
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@BookShelfActivity::class.java.name)
            val currentTracker = BookShelfTracker(
                category,
                position,
                offset
            )
            dataStoreManager.saveTracker(
                DataStoreManager.BOOK_SHELF_KEY,
                currentTracker,
                true,
                addToHistory
            )
        }
    }

    fun updateCoverForShaInAdapter(updatedSha: String) {
        // Update only items that match this SHA
        bookRowAdapter.updateCoverForSha(updatedSha)
    }

    private fun shelfShowBookInfoOverlay(pdfFile: File) {
        showBookInfoOverlay(pdfFile)
    }

    private fun getBookShelfBooks(category: String): List<FileItem> {
        return repository.getBookShelfBooks(category)
    }

    override fun onPause() {
        super.onPause()
        saveBookShelfTracker(currentCategory, lastScrollPosition, lastScrollOffset)
    }
}