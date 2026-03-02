package apk.hurnell.recipebookreader

import android.app.AlertDialog
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.AllBookmarkAdapter
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BookmarkItem
import com.google.android.material.snackbar.Snackbar
import java.io.File

data class BookmarksPositionTracker(
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
)

class BookmarksActivity : BaseDrawerActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var bookmarkAdapter: AllBookmarkAdapter
    private lateinit var rootLayout: CoordinatorLayout
    private var justStarted: Boolean = true
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private val configurationKey = "BookmarksConfiguration"
    private var bookmarkData: List<BookmarkItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_bookmarks)
        repository = PdfRepository(this)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        val toolbar: Toolbar = findViewById(R.id.bookmarksToolbar)

        recyclerView = findViewById(R.id.bookmarksRecyclerView)
        rootLayout = findViewById(R.id.rootLayout)
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
        bookmarkData = reloadBookmarks(false)
        bookmarkAdapter = AllBookmarkAdapter(
            onClick = { item ->
                if (item.bookLocation != null) {
                    val file = File(item.bookLocation)
                    processAndOpenBook(file, item)
                }
            },
            onDeleteClick = { item ->
                checkDeleteBookmark(item)
            },
            onLongClick = { item ->
                displayClickResult(item.title, rootLayout)
            }
        )
        recyclerView.adapter = bookmarkAdapter
        bookmarkAdapter.submitList(bookmarkData)
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
        AlertDialog.Builder(this)
            .setTitle("Already Bookmarked")
            .setMessage("Are you sure you want to delete this bookmark")
            .setPositiveButton("Delete") { dialog, _ ->
                val success = repository.deleteBookmark(item)
                if (success) {
                    val toastText = "❌ Bookmark with title ${item.title} deleted"
                    Toast.makeText(
                        this,
                        toastText,
                        Toast.LENGTH_SHORT
                    ).show()
                    this@BookmarksActivity.reloadBookmarks(true)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun reloadBookmarks(applyAfter: Boolean): List<BookmarkItem> {
        val bookmarks = repository.getAllBookmarks()

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
        saveBookmarksConfiguration()
    }

    private fun saveBookmarksConfiguration() {
        val configData = BookmarksPositionTracker(
            lastScrollPosition,
            lastScrollOffset
        )
        if (!justStarted) {
            repository.saveConfiguration(configurationKey, configData)
        }
        justStarted = false
    }

    override fun refreshFilesAndUI() {

    }
}