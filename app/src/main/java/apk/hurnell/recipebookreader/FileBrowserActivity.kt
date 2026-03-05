package apk.hurnell.recipebookreader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.FileAdapter
import apk.hurnell.recipebookreader.model.FileItem
import java.io.File
import androidx.core.graphics.scale
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class FileBrowserTracker(
    val directory: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()


class FileBrowserActivity : BaseDrawerActivity() {
    private var pdfOnly: Boolean = false
    private var targetSha: String? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var breadcrumbLayout: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private val rootDir = Environment.getExternalStorageDirectory()
    private var currentDir: File = File(rootDir, "Documents")
    private var lastScrollPosition: Int = 0
    private var lastScrollOffset: Int = 0

    companion object {
        const val EXTRA_PDF_ONLY = "extra_pdf_only"
        const val EXTRA_TARGET_SHA = "extra_target_sha"
        const val NOT_FROM_NAVIGATION_EVENT = "not_from_navigation_event"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_file_browser)
        pdfOnly = intent.getBooleanExtra(EXTRA_PDF_ONLY, true)
        targetSha = intent.getStringExtra(EXTRA_TARGET_SHA)
        val notFromNavigationEvent = intent.getBooleanExtra(NOT_FROM_NAVIGATION_EVENT, true)
        if (notFromNavigationEvent) {
            navigateBackToSavedActivity()
        }

        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingOverlay.visibility = View.GONE
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)
        drawerLayout.closeDrawer(GravityCompat.START, false)
        breadcrumbLayout = findViewById(R.id.breadcrumbLayout)
        breadcrumbScroll = findViewById(R.id.breadcrumbScroll)
        recyclerView = findViewById(R.id.fileRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(
            onClick = { file -> onFileClick(file) },
            onLongClick = { file -> browserShowBookInfoOverlay(file) },
            repository,
            pdfOnly
        )
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        requestStoragePermission()
        applySavedTracker(pdfOnly)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pdfOnly && currentDir.absolutePath != rootDir.absolutePath) {
                    val parent = currentDir.parentFile
                    if (parent != null) showFiles(parent)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

    }

    private fun navigateBackToSavedActivity() {
        val dataStoreManager = DataStoreManager(applicationContext)

        lifecycleScope.launch {
            // Get the first emitted value from the Flow
            val lastActivityName = dataStoreManager.lastActivityFlow.first()
            val simpleActivities = arrayOf(
                "apk.hurnell.recipebookreader.BookmarksActivity",
                "apk.hurnell.recipebookreader.BookShelfActivity",
                "apk.hurnell.recipebookreader.EveryTocActivity",
                "apk.hurnell.recipebookreader.RecentBooksActivity",

                )
            if (!lastActivityName.isNullOrEmpty()) {

                try {
                    if (lastActivityName in simpleActivities) {
                        val targetClass = Class.forName(lastActivityName)
                        startActivity(Intent(this@FileBrowserActivity, targetClass))
                        finish()
                    }
                    if (lastActivityName == "apk.hurnell.recipebookreader.RecipeBookActivity") {
                        navigateToSavedRecipeBookState()
                    }
                } catch (e: ClassNotFoundException) {
                    // Handle case where activity no longer exists
                }
            }
        }
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
        val offset = firstVisibleView?.top ?: 0
        lastScrollPosition = firstVisibleItemPosition
        lastScrollOffset = offset
    }

    fun applySavedTracker(pdfOnly: Boolean) {
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            val tracker =
                if (pdfOnly) dataStoreManager.pdfFileBrowserState.firstOrNull() else dataStoreManager.imageFileBrowserState.firstOrNull()
            navigateToSavedDirectory(tracker)
        }
    }

    private fun navigateToSavedDirectory(tracker: FileBrowserTracker?) {

        val savedDir = tracker?.let { File(it.directory) }
        var ignoreSavedPosition: Boolean
        currentDir = if (savedDir != null && savedDir.exists()) {
            ignoreSavedPosition = false
            savedDir
        } else {
            ignoreSavedPosition = true
            File(rootDir, "Documents")
        }
        showFiles(currentDir, ignoreSavedPosition, tracker)
    }

    private fun browserShowBookInfoOverlay(file: File) {
        if (file.isDirectory) {
            showFiles(file)
            return
        }

        showBookInfoOverlay(file)
    }

    private fun showImageActionDialog(file: File) {
        val dialogView = layoutInflater.inflate(R.layout.cover_image_preview, null)
        val previewImage = dialogView.findViewById<ImageView>(R.id.previewImage)
        val btnAccept = dialogView.findViewById<Button>(R.id.btnAccept)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
        previewImage.setImageBitmap(originalBitmap)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            originalBitmap.recycle() // Clean up memory if they cancel
            dialog.dismiss()
        }

        btnAccept.setOnClickListener {
            val targetWidth = 200
            val targetHeight = 300

            val scaledBitmap = originalBitmap.scale(targetWidth, targetHeight)

            val saveSha = targetSha ?: file.nameWithoutExtension
            val success = saveBitmapAsCover(scaledBitmap, saveSha)


            originalBitmap.recycle()
            scaledBitmap.recycle()
            dialog.dismiss()
            if (success) {
                val resultIntent = Intent().apply {
                    putExtra("updated_sha", saveSha)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        refreshFilesAndUI()
    }

    override fun refreshFilesAndUI() {
        if (findViewById<DrawerLayout>(R.id.drawer_layout) != null) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }
        showFiles(currentDir)
    }

    private fun requestStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        }
    }

    fun isFileType(extension: String): Boolean {
        val isPdf = extension.equals("pdf", true)
        if (pdfOnly) {
            return isPdf
        }
        val isPng = extension.equals("png", true)
        val isJpg = extension.equals("jpg", true)
        val isJpeg = extension.equals("jpeg", true)
        return isJpg || isPng || isJpeg
    }

    private fun showFiles(
        dir: File,
        ignoreSavedPosition: Boolean = true,
        saved: FileBrowserTracker? = null
    ) {
        currentDir = dir
        val items = dir.listFiles()
            ?.filter {
                it.canRead() && (it.isDirectory || isFileType(it.extension))
            }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { file ->
                val bookInfo =
                    if (!file.isDirectory) repository.getBookInfoForItemPath(file.path) else null
                FileItem(file, file.name, bookInfo, System.currentTimeMillis())
            } ?: emptyList()
        if (ignoreSavedPosition) {
            adapter.submitList(items)
        } else {
            val ensuredSaved = saved!!
            adapter.submitList(items) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(
                    ensuredSaved.lastScrollPosition,
                    ensuredSaved.lastScrollOffset
                )
            }
        }
        updateBreadcrumb(currentDir)
    }

    private fun onFileClick(file: File) {
        if (file.isDirectory) {
            showFiles(file)
        } else if (pdfOnly && file.extension.equals("pdf", ignoreCase = true)) {
            processAndOpenBook(file)
        } else if (!pdfOnly) {
            showImageActionDialog(file)
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

    override fun onPause() {
        super.onPause()
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@FileBrowserActivity::class.java.name)
            val currentTracker = FileBrowserTracker(
                currentDir.absolutePath,
                lastScrollPosition,
                lastScrollOffset
            )
            val key: Preferences.Key<String> =
                if (pdfOnly) DataStoreManager.PDF_FILE_BROWSER_KEY else DataStoreManager.IMAGE_FILE_BROWSER_KEY
            dataStoreManager.saveTracker(key, currentTracker)
        }
    }

    fun navigateToSavedRecipeBookState() {
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            val tracker = dataStoreManager.recipeBookState.firstOrNull()
            if (tracker != null) {

                val intent =
                    Intent(this@FileBrowserActivity, RecipeBookActivity::class.java).apply {
                        putExtra("PDF_PATH", tracker.location)
                        val savedStateJson = tracker.let { Gson().toJson(it) }
                        if (savedStateJson != null) {
                            putExtra("SAVED_STATE_JSON", savedStateJson)
                        }
                    }
                startActivity(intent)
            }
        }
    }
}