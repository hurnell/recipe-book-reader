package apk.hurnell.recipebookreader

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.FileAdapter
import apk.hurnell.recipebookreader.model.FileItem
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.RecentFile
import java.io.File

class RecentFilesActivity : BaseDrawerActivity() {

    private lateinit var adapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent_files)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)

        val recyclerView = findViewById<RecyclerView>(R.id.recentRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        repository = PdfRepository(this)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        adapter = FileAdapter(
            onClick = { file -> onFileClick(file) },
            onLongClick = { file -> showBookInfoOverlay(file) },
            repository = repository
        )
        recyclerView.adapter = adapter
        val recentFiles: List<RecentFile> = repository.getRecentFiles()

        refreshFileList()
    }

    override fun refreshFilesAndUI() {

    }

    fun refreshFileList(){
        val recentFiles: List<RecentFile> = repository.getRecentFiles()

        val fileItems: List<FileItem> = recentFiles.mapNotNull { recent ->
            val file = File(recent.location)
            if (file.exists()) {
                FileItem(
                    file = file,
                    displayName = file.name,
                    bookInfo = repository.getBookInfoForItemPath(file.path),
                    false
                )
            } else {
                null
            }
        }
        adapter.submitList(fileItems)
    }

    private fun onFileClick(file: File) {
        processAndOpenBook(file)
    }
    override fun onResume() {
        super.onResume()
        if (findViewById<DrawerLayout>(R.id.drawer_layout) != null) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }
        refreshFileList()
    }


}