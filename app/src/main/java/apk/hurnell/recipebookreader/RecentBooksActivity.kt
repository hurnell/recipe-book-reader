package apk.hurnell.recipebookreader

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.FileAdapter
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.FileItem
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.RecentFile
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

data class RecentBooksTracker(
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()

class RecentBooksActivity : BaseDrawerActivity() {

    private lateinit var adapter: FileAdapter
    private lateinit var recyclerView: RecyclerView
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private var justStarted: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent_files)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)

        recyclerView = findViewById(R.id.recentRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        loadingOverlay = findViewById(R.id.loadingOverlay)
        adapter = FileAdapter(
            onClick = { file -> onFileClick(file) },
            onLongClick = { file -> showBookInfoOverlay(file) },
            repository = repository,
            true
        )
        recyclerView.adapter = adapter
        val fileItems = refreshFileList()
        applyRecentBooksSavedSettings(fileItems)

    }

    fun applyRecentBooksSavedSettings(fileItems: List<FileItem>) {
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            val tracker = dataStoreManager.recentBooksState.firstOrNull()

            if (tracker != null) {
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset

                adapter.submitList(fileItems) {
                    (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(lastScrollPosition, lastScrollOffset)
                    recyclerView.post { justStarted = false }
                }
            } else {
                adapter.submitList(fileItems)
                justStarted = false
            }
        }
    }

    private fun trackRecyclerViewOffset() {
        if (!justStarted) {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
            val offset = firstVisibleView?.top ?: 0
            lastScrollPosition = firstVisibleItemPosition
            lastScrollOffset = offset
        }

    }

    override fun refreshFilesAndUI() {

    }

    fun refreshFileList(): List<FileItem> {
        val recentFiles: List<RecentFile> = repository.getRecentFiles()

        val fileItems: List<FileItem> = recentFiles.mapNotNull { recent ->
            val file = File(recent.location)
            if (file.exists()) {
                FileItem(
                    file = file,
                    displayName = file.name,
                    bookInfo = repository.getBookInfoForItemPath(file.path),
                    System.currentTimeMillis()
                )
            } else {
                null
            }
        }
        return fileItems
    }

    private fun onFileClick(file: File) {
        processAndOpenBook(file)
    }

    override fun onResume() {
        super.onResume()
        if (findViewById<DrawerLayout>(R.id.drawer_layout) != null) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }
    }


    override fun onPause() {
        recyclerView.clearOnScrollListeners()
        super.onPause()
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@RecentBooksActivity::class.java.name)
            val currentTracker = RecentBooksTracker(
                lastScrollPosition,
                lastScrollOffset
            )
            dataStoreManager.saveTracker(DataStoreManager.RECENT_BOOKS_KEY, currentTracker)
        }
    }
}