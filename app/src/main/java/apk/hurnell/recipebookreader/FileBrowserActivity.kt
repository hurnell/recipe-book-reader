package apk.hurnell.recipebookreader

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
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
import androidx.lifecycle.lifecycleScope
import apk.hurnell.recipebookreader.databinding.ActivityFileBrowserBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class FileBrowserTracker(
    @SerializedName("directory") val directory: String,
    @SerializedName("lastScrollPosition") val lastScrollPosition: Int,
    @SerializedName("lastScrollOffset") val lastScrollOffset: Int
) : BaseTracker()


class FileBrowserActivity : BaseDrawerActivity() {

    private var _binding: ActivityFileBrowserBinding? = null
    private val binding get() = _binding!!
    private var targetSha: String? = null
    private var targetBookName: String? = null
    private lateinit var adapter: FileAdapter
    private var lastScrollPosition: Int = 0
    private var lastScrollOffset: Int = 0

    companion object {
        const val EXTRA_PDF_ONLY = "extra_pdf_only"
        const val EXTRA_TARGET_SHA = "extra_target_sha"
        const val EXTRA_TARGET_BOOK_NAME = "extra_target_book_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityFileBrowserBinding.inflate(layoutInflater)

        setContentView(binding.root)
        pdfOnly = intent.getBooleanExtra(EXTRA_PDF_ONLY, true)
        targetSha = intent.getStringExtra(EXTRA_TARGET_SHA)
        targetBookName = intent.getStringExtra(EXTRA_TARGET_BOOK_NAME)
        val stopAddToHistory = intent.getBooleanExtra("STOP_ADD_TO_HISTORY", false)
        if (stopAddToHistory) {
            addToHistory = false
        }

        loadingOverlay = binding.loadingOverlay
        loadingOverlay.visibility = View.GONE
        setupDrawer(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        drawerLayout.closeDrawer(GravityCompat.START, false)
        binding.fileRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(
            onClick = { file -> onFileClick(file) },
            onLongClick = { file -> browserShowBookInfoOverlay(file) },
            repository,
            pdfOnly
        )
        binding.fileRecyclerView.adapter = adapter
        binding.fileRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        requestStoragePermission()
        applySavedTracker(pdfOnly)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation(this, ignoreHistory = false)
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        val directoryIcon = binding.toolbar.findViewById<ImageButton>(R.id.fileBrowserHistoryIcon)
        directoryIcon.visibility = View.VISIBLE
        directoryIcon.setOnClickListener {
            addToHistory = false
            handleBackNavigation(backCallback, ignoreHistory = true)
        }

    }

    private fun handleBackNavigation(
        callback: OnBackPressedCallback,
        ignoreHistory: Boolean = false
    ) {
        val wasOpen = checkCloseDrawerIsOpen()
        if (wasOpen) return
        val deleteTracker = FileBrowserTracker(
            currentDir.absolutePath,
            0,
            0
        )
        if (pdfOnly && currentDir.absolutePath != rootDir.absolutePath && !ignoreHistory) {
            val parent = currentDir.parentFile
            addToHistory = false
            lifecycleScope.launch {
                dataStoreManager.deleteCurrentFileDirectory(pdfOnly, deleteTracker, true)
            }
            if (parent != null) {
                showFiles(parent)
            }
        } else if(!fullyCloseFromFileBrowser){
            handleBackPressLogic(true){
                addToHistory = false
                lifecycleScope.launch {
                    dataStoreManager.deleteCurrentFileDirectory(pdfOnly, deleteTracker, true)
                }
                callback.isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                callback.isEnabled = true
            }
        } else {
            finish()
        }
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager = binding.fileRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
        val offset = firstVisibleView?.top ?: 0
        lastScrollPosition = firstVisibleItemPosition
        lastScrollOffset = offset
    }

    fun applySavedTracker(pdfOnly: Boolean) {
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
        showFiles(currentDir, ignoreSavedPosition, tracker, true)
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
        val previewName = dialogView.findViewById<TextView>(R.id.previewBookName)
        val btnAccept = dialogView.findViewById<Button>(R.id.btnAccept)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        previewName.text = targetBookName
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
        previewImage.setImageBitmap(originalBitmap)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            originalBitmap.recycle()
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
                repository.updateIsAlternateCover(saveSha, 1)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        addToHistory = false
        refreshFilesAndUI(currentDir.absolutePath != initialDir.absolutePath)
    }

    override fun refreshFilesAndUI(reloadAdapter: Boolean) {
        if (findViewById<DrawerLayout>(R.id.drawer_layout) != null) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }
        if (reloadAdapter) {
            showFiles(currentDir)
        }
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

    private fun reinstateLastFolderPosition(items: List<FileItem>, targetPath: String) {
        lifecycleScope.launch {
            val savedState =
                dataStoreManager.findTrackerInHistoryByDirectory(targetPath, addToHistory)
            if (savedState != null) {
                adapter.submitList(items) {
                    val layoutManager =
                        binding.fileRecyclerView.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(
                        savedState.lastScrollPosition,
                        savedState.lastScrollOffset
                    )
                }
            }
        }

    }


    private fun showFiles(
        dir: File,
        ignoreSavedPosition: Boolean = true,
        saved: FileBrowserTracker? = null,
        isStart: Boolean = false
    ) {
        if (saved == null) {
            saveLastFolderTracker(
                currentDir.absolutePath,
                lastScrollPosition,
                lastScrollOffset,
                false
            )
        }
        currentDir = dir
        if (saved == null) {
            saveLastFolderTracker(
                currentDir.absolutePath,
                0,
                0,
                true
            )
        }
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
                val layoutManager = binding.fileRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(
                    ensuredSaved.lastScrollPosition,
                    ensuredSaved.lastScrollOffset
                )
            }
        }
        updateBreadcrumb(currentDir)
        if (saved == null) {
            reinstateLastFolderPosition(items, currentDir.absolutePath)
        }
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
        binding.breadcrumbLayout.removeAllViews()
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
                setTextColor(ContextCompat.getColor(context, R.color.dark_text))
                setOnClickListener {
                    if (file != currentDir) {
                        lifecycleScope.launch {
                            val deleteTracker = FileBrowserTracker(
                                file.absolutePath,
                                0,
                                0
                            )
                            addToHistory = false
                            dataStoreManager.deleteCurrentFileDirectory(pdfOnly,deleteTracker, true)
                            showFiles(file)
                        }
                    }
                }
            }
            binding.breadcrumbLayout.addView(textView)

            if (index != pathList.lastIndex) {
                val pointer = TextView(this).apply {
                    text = ">"
                    setTextColor(ContextCompat.getColor(context, R.color.dark_text))
                }
                binding.breadcrumbLayout.addView(pointer)
            }
        }
        binding.breadcrumbScroll.post { binding.breadcrumbScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }
    }

    fun resetToRoot() {
        showFiles(rootDir)
        drawerLayout.closeDrawers()
    }


    private fun saveLastFolderTracker(
        directory: String,
        position: Int,
        offset: Int,
        saveCurrent: Boolean
    ) {
        lifecycleScope.launch {
            val key: Preferences.Key<String> =
                if (pdfOnly) DataStoreManager.PDF_FILE_BROWSER_KEY else DataStoreManager.IMAGE_FILE_BROWSER_KEY
            val total = dataStoreManager.getTotalEntries(key.name, true)
            if (total > 0 && directory == initialDir.absolutePath) {
                return@launch
            }
            dataStoreManager.saveLastActivity(this@FileBrowserActivity::class.java.name)
            val currentTracker = FileBrowserTracker(
                directory,
                position,
                offset
            )
            dataStoreManager.saveTracker(key, currentTracker, saveCurrent, addToHistory)
            addToHistory = true
        }
    }


    override fun onPause() {
        super.onPause()
        saveLastFolderTracker(
            currentDir.absolutePath,
            lastScrollPosition,
            lastScrollOffset,
            true
        )
    }

}