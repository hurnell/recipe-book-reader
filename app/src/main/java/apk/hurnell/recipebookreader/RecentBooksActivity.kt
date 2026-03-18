package apk.hurnell.recipebookreader

import android.os.Bundle
import android.view.View
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
import apk.hurnell.recipebookreader.databinding.ActivityRecentBooksBinding

data class RecentBooksTracker(
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()

class RecentBooksActivity : BaseDrawerActivity() {

    private var _binding: ActivityRecentBooksBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileAdapter
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private var justStarted: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityRecentBooksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupDrawer(binding.recentFilesToolbar)

        binding.recentRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.recentRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        loadingOverlay = binding.loadingOverlay
        loadingOverlay.visibility = View.GONE
        adapter = FileAdapter(
            onClick = { file -> onFileClick(file) },
            onLongClick = { file -> showBookInfoOverlay(file) },
            repository = repository,
            true
        )
        binding.recentRecyclerView.adapter = adapter
        val fileItems = refreshFileList()
        applyRecentBooksSavedSettings(fileItems)

    }

    fun applyRecentBooksSavedSettings(fileItems: List<FileItem>) {
        lifecycleScope.launch {
            val tracker = dataStoreManager.recentBooksState.firstOrNull()
            if (tracker != null) {
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset

                adapter.submitList(fileItems) {
                    (binding.recentRecyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(lastScrollPosition, lastScrollOffset)
                    binding.recentRecyclerView.post { justStarted = false }
                }
            } else {
                adapter.submitList(fileItems)
                justStarted = false
            }
        }
    }

    private fun trackRecyclerViewOffset() {
        if (!justStarted) {
            val layoutManager =
                binding.recentRecyclerView.layoutManager as? LinearLayoutManager ?: return
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
            val offset = firstVisibleView?.top ?: 0
            lastScrollPosition = firstVisibleItemPosition
            lastScrollOffset = offset
        }

    }

    override fun refreshFilesAndUI(reloadAdapter: Boolean) {

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

    private fun saveRecentBooksTracker(
        position: Int,
        offset: Int
    ) {
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@RecentBooksActivity::class.java.name)
            val currentTracker = RecentBooksTracker(
                position,
                offset
            )
            dataStoreManager.saveTracker(
                DataStoreManager.RECENT_BOOKS_KEY,
                currentTracker,
                true,
                addToHistory
            )
        }
    }

    override fun onPause() {
        binding.recentRecyclerView.clearOnScrollListeners()
        super.onPause()
        saveRecentBooksTracker(lastScrollPosition, lastScrollOffset)
    }
}