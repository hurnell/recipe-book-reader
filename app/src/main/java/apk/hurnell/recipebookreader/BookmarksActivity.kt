package apk.hurnell.recipebookreader

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.AllBookmarkAdapter
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.BookmarkItem
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

data class BookmarksTracker(
    val currentCategory: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()

class BookmarksActivity : BaseDrawerActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var bookmarkAdapter: AllBookmarkAdapter
    private lateinit var rootLayout: CoordinatorLayout
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private val configurationKey = "BookmarksConfiguration"
    private var bookmarkData: List<BookmarkItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_bookmarks)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingOverlay.visibility = View.GONE

        val toolbar: Toolbar = findViewById(R.id.bookmarksToolbar)

        spinner = findViewById(R.id.categorySpinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentCategory = parent.getItemAtPosition(position) as String
                reloadBookmarks(true)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                currentCategory = "All"
                reloadBookmarks(true)
            }
        }
        recyclerView = findViewById(R.id.bookmarksRecyclerView)
        rootLayout = findViewById(R.id.rootLayout)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        bookmarkData = reloadBookmarks(false)
        bookmarkAdapter = AllBookmarkAdapter(onClick = { item ->
            if (item.bookLocation != null) {
                val file = File(item.bookLocation)
                processAndOpenBook(file, item)
            }
        }, onDeleteClick = { item ->
            checkDeleteBookmark(item)
        }, onLongClick = { item ->
            displayClickResult(item.title, rootLayout)
        })
        recyclerView.adapter = bookmarkAdapter
        populateAdapter()
        setupDrawer(toolbar)
    }

    fun displayClickResult(text: String, rootLayout: CoordinatorLayout) {
        val snackBar = Snackbar.make(rootLayout, text, Snackbar.LENGTH_LONG)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 5
        snackBar.show()
    }

    private fun checkDeleteBookmark(item: BookmarkItem) {
        AlertDialog.Builder(this).setTitle("Delete Bookmark?")
            .setMessage("Are you sure you want to delete this bookmark")
            .setPositiveButton("Delete") { dialog, _ ->
                val success = repository.deleteBookmark(item)
                if (success) {
                    val toastText = "❌ Bookmark with title ${item.title} deleted"
                    Toast.makeText(
                        this, toastText, Toast.LENGTH_SHORT
                    ).show()
                    this@BookmarksActivity.reloadBookmarks(true)
                }
                dialog.dismiss()
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun reloadBookmarks(applyAfter: Boolean): List<BookmarkItem> {
        val bookmarks = repository.getAllBookmarks(currentCategory)
        if (applyAfter) {
            bookmarkAdapter.submitList(bookmarks)
        }
        return bookmarks
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
        val offset = firstVisibleView?.top ?: 0

        lastScrollPosition = firstVisibleItemPosition
        lastScrollOffset = offset
    }

    fun populateAdapter() {
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            val tracker = dataStoreManager.bookmarksState.firstOrNull()
            if (tracker != null) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset
                currentCategory = tracker.currentCategory
                layoutManager?.scrollToPositionWithOffset(
                    lastScrollPosition, lastScrollOffset
                )
                val position = categories.indexOf(currentCategory)
                spinner.setSelection(position)
                reloadBookmarks(true)
            }
        }
        bookmarkAdapter.submitList(bookmarkData)

        refreshCategories()
    }

    override fun refreshFilesAndUI() {

    }

    override fun onPause() {
        super.onPause()
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@BookmarksActivity::class.java.name)
            val currentTracker = BookmarksTracker(
                currentCategory,
                lastScrollPosition,
                lastScrollOffset
            )
            dataStoreManager.saveTracker(DataStoreManager.BOOKMARKS_KEY, currentTracker)
        }
    }
}