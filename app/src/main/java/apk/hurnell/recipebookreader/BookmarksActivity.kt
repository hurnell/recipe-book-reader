package apk.hurnell.recipebookreader

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.AllBookmarkAdapter
import apk.hurnell.recipebookreader.databinding.ActivityBookmarksBinding
import apk.hurnell.recipebookreader.databinding.DialogBookmarkBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.BookmarkItem
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

data class BookmarksTracker(
    val currentCategory: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()

class BookmarksActivity : BaseDrawerActivity() {

    private var _binding: ActivityBookmarksBinding? = null
    private val binding get() = _binding!!
    private lateinit var bookmarkAdapter: AllBookmarkAdapter
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private val configurationKey = "BookmarksConfiguration"
    private var bookmarkData: List<BookmarkItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityBookmarksBinding.inflate(layoutInflater)
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
                currentCategory = parent.getItemAtPosition(position) as String
                reloadBookmarks(true)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                currentCategory = "All"
                reloadBookmarks(true)
            }
        }
        binding.bookmarksRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.bookmarksRecyclerView.itemAnimator = null
        binding.bookmarksRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
        }, onEditClick = { item ->
            editBookmark(item)
        }, onLongClick = { item ->
            displayClickResult(item.title, binding.rootLayout)
        })
        binding.bookmarksRecyclerView.adapter = bookmarkAdapter
        populateAdapter()
        setupDrawer(binding.bookmarksToolbar)
    }

    fun displayClickResult(text: String, rootLayout: CoordinatorLayout) {
        val snackBar = Snackbar.make(binding.rootLayout, text, Snackbar.LENGTH_LONG)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 5
        snackBar.show()
    }

    private fun editBookmark(item: BookmarkItem) {
        val dialogBinding = DialogBookmarkBinding.inflate(layoutInflater)
        dialogBinding.enterBookmarkText.setText(item.title)
        val dialog = AlertDialog.Builder(this@BookmarksActivity)
            .setTitle("Update Bookmark")
            .setView(dialogBinding.root)
            .setPositiveButton("Update") { _, _ ->
                val bookmarkText = dialogBinding.enterBookmarkText.text.toString()

                if (bookmarkText.isNotBlank()) {
                    item.title = bookmarkText
                    completeBookmarkEdit(
                        item
                    )
                } else {
                    Toast.makeText(
                        this@BookmarksActivity,
                        "Name cannot be empty",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(), // 90% of screen width
            ViewGroup.LayoutParams.WRAP_CONTENT // height wraps content
        )
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )
    }

    private fun completeBookmarkEdit(item: BookmarkItem){
        val bookmarks = repository.updateBookmark(item, currentCategory)
        bookmarkAdapter.submitList(bookmarks)
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
        val layoutManager = binding.bookmarksRecyclerView.layoutManager as? LinearLayoutManager ?: return
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
                val layoutManager = binding.bookmarksRecyclerView.layoutManager as? LinearLayoutManager
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