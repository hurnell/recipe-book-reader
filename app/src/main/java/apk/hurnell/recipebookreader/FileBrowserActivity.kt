package apk.hurnell.recipebookreader

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.FileAdapter
import apk.hurnell.recipebookreader.adapters.FileItem
import apk.hurnell.recipebookreader.helpers.PdfStreamer
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import apk.hurnell.recipebookreader.helpers.DatabaseHelper

class FileBrowserActivity : BaseDrawerActivity() {
    private lateinit var loadingOverlay: LinearLayout
    private val dbHelper by lazy { DatabaseHelper(this) }
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var breadcrumbLayout: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private lateinit var overlayContainer: FrameLayout
    private lateinit var fileInfoOverlay: ScrollView
    private lateinit var fileInfoTitle: TextView
    private lateinit var fileInfoContent: TextView
    private val rootDir = Environment.getExternalStorageDirectory()
    private var currentDir: File = File(rootDir, "Documents/moon/moon/asian")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)
        drawerLayout.closeDrawer(GravityCompat.START, false)
        breadcrumbLayout = findViewById(R.id.breadcrumbLayout)
        breadcrumbScroll = findViewById(R.id.breadcrumbScroll)
        recyclerView = findViewById(R.id.fileRecyclerView)
        overlayContainer = findViewById(R.id.overlayContainer)
        fileInfoOverlay = findViewById(R.id.fileInfoOverlay)
        fileInfoTitle = findViewById(R.id.fileInfoTitle)
        overlayContainer.setOnClickListener {
            hideFileInfoOverlay()
        }
        fileInfoContent = findViewById(R.id.fileInfoContent)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(
            onClick = { file -> onFileClick(file) },
            onLongClick = { file -> showFileInfoOverlay(file) }
        )
        recyclerView.adapter = adapter

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentDir.absolutePath != rootDir.absolutePath) {
                    val parent = currentDir.parentFile
                    if (parent != null) showFiles(parent)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        requestStoragePermission()
    }
    private fun showFileInfoOverlay(file: File) {
        fileInfoTitle.text = file.name
        fileInfoContent.text = buildString {
            append("Path: ${file.absolutePath}\n")
            append("Size: ${if (file.isFile) "${file.length()} bytes" else "Folder"}\n")
            append("Readable: ${file.canRead()}\n")
            append("Writable: ${file.canWrite()}\n")
            append("Last modified: ${java.util.Date(file.lastModified())}\n")
        }

        overlayContainer.visibility = View.VISIBLE
        fileInfoOverlay.scaleX = 0.8f
        fileInfoOverlay.scaleY = 0.8f
        fileInfoOverlay.alpha = 0f
        fileInfoOverlay.visibility = View.VISIBLE
        fileInfoOverlay.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250)
            .start()
    }

    private fun hideFileInfoOverlay() {
        fileInfoOverlay.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(200)
            .withEndAction {
                fileInfoOverlay.visibility = View.GONE
                overlayContainer.visibility = View.GONE
            }.start()
    }
    override fun onResume() {
        super.onResume()
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
            if (file.isDirectory) {
                showFiles(file)
            } else if (file.extension.equals("pdf", ignoreCase = true)) {
                processAndOpenBook(file)
            }
        }
    }

    private fun processAndOpenBook(pdfFile: File) {
        loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = PdfStreamer(contentResolver, pdfFile.toUri())
                val document = Document.openDocument(stream, "application/pdf")

                dbHelper.checkAddBookToDatabase(pdfFile, pdfFile.absolutePath, document)

                val dbFile = getDatabasePath("recipe-reader.db")
                val exportFile = File(getExternalFilesDir(null), "recipe-reader.db")
                dbFile.copyTo(exportFile, overwrite = true)

                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    val intent =
                        Intent(this@FileBrowserActivity, RecipeBookActivity::class.java).apply {
                            putExtra("PDF_PATH", pdfFile.absolutePath)
                        }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        this@FileBrowserActivity,
                        "Error loading PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("NIGEL_HURNELL", "Processing failed ${e.message}", e)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    fun resetToRoot() {
        showFiles(rootDir)
        drawerLayout.closeDrawers()
    }
}