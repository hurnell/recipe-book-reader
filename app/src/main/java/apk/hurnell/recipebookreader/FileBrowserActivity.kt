package apk.hurnell.recipebookreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.FileAdapter
import apk.hurnell.recipebookreader.adapters.FileItem
import java.io.File

// 1. Inherit from BaseDrawerActivity
class FileBrowserActivity : BaseDrawerActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var breadcrumbLayout: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView

    private val rootDir = Environment.getExternalStorageDirectory()
    private var currentDir: File = File(rootDir, "Documents/moon/moon/spanish")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)

        // 2. Setup the Base Drawer logic
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)
        drawerLayout.closeDrawer(GravityCompat.START, false)
        // 3. UI Component Initialization
        breadcrumbLayout = findViewById(R.id.breadcrumbLayout)
        breadcrumbScroll = findViewById(R.id.breadcrumbScroll)
        recyclerView = findViewById(R.id.fileRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter { file -> onFileClick(file) }
        recyclerView.adapter = adapter

        // 4. Custom Back Navigation (Overrides Base logic for folder climbing)
        // Inside onCreate of FileBrowserActivity
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // We don't need to check drawerLayout here; BaseDrawerActivity handles it!
                if (currentDir.absolutePath != rootDir.absolutePath) {
                    val parent = currentDir.parentFile
                    if (parent != null) showFiles(parent)
                } else {
                    // Exit activity
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        requestStoragePermission()
    }

    override fun onResume() {
        super.onResume()
        // We check if the drawer is actually attached to the window
        // to avoid "lateinit not initialized" errors.
        if (findViewById<DrawerLayout>(R.id.drawer_layout) != null) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }
    }

    private fun requestStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        } else {
            showFiles(currentDir)
        }
    }

    private fun showFiles(dir: File) {
        currentDir = dir

        val items = dir.listFiles()
            ?.filter {
                it.canRead() && (it.isDirectory || it.extension.equals("pdf", ignoreCase = true))
            }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { file ->
                FileItem(file, file.name)
            } ?: emptyList()

        adapter.submitList(items)
        updateBreadcrumb(currentDir)
    }

    private fun onFileClick(file: File) {
        if (file.isDirectory) {
            showFiles(file)
        } else if (file.extension.equals("pdf", ignoreCase = true)) {
            val tempFile = File(cacheDir, file.name)
            if (!tempFile.exists()) {
                contentResolver.openInputStream(file.toUri())?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val contentUri = getFileContentUri(tempFile)
            // TODO: Start RecipeBookActivity with contentUri
        }
    }

    private fun getFileContentUri(file: File): Uri {
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // When we navigate back here from the drawer, reset to the root folder
        resetToRoot()
    }

    private fun updateBreadcrumb(dir: File) {
        breadcrumbLayout.removeAllViews()
        val pathList = mutableListOf<File>()
        var temp: File? = dir

        while (temp != null && temp.path.startsWith(rootDir.path)) {
            pathList.add(0, temp)
            temp = temp.parentFile
        }

        pathList.forEachIndexed { index, file ->
            val textView = TextView(this).apply {
                text = if (file.absolutePath == rootDir.absolutePath) "Root" else file.name
                setPadding(16, 8, 16, 8)
                setOnClickListener { if (file != currentDir) showFiles(file) }
            }
            breadcrumbLayout.addView(textView)

            if (index != pathList.lastIndex) {
                breadcrumbLayout.addView(TextView(this).apply { text = ">" })
            }
        }
        breadcrumbScroll.post { breadcrumbScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }
    }

    // This is called by ControlFragment
    fun resetToRoot() {
        showFiles(rootDir)
        drawerLayout.closeDrawers()
    }
}