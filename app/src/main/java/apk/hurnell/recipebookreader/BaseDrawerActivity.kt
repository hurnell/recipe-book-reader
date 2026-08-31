package apk.hurnell.recipebookreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.CoverPickerAdapter
import apk.hurnell.recipebookreader.databinding.ListItemSubCategoryBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.helpers.GetCoverUrlHelper
import apk.hurnell.recipebookreader.helpers.HistoryEntry
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.BaseBookmarkTocItem
import apk.hurnell.recipebookreader.model.Book
import apk.hurnell.recipebookreader.model.CategoryItem
import apk.hurnell.recipebookreader.ui.EditableCategoryView
import apk.hurnell.recipebookreader.ui.EditableTextView
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Pixmap
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredTextWalker
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.bumptech.glide.Glide
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

abstract class BaseDrawerActivity : AppCompatActivity() {
    protected val dataStoreManager by lazy { DataStoreManager(applicationContext) }

    lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    protected var pdfOnly: Boolean = false
    protected lateinit var repository: PdfRepository
    protected var categories = mutableListOf<String>()
    protected var currentCategory = "All"
    protected lateinit var spinner: Spinner
    protected var closeAppConfirmed: Boolean = false

    protected var currentSearchTerm: String = "" /// TOC SEARCH TERM
    protected lateinit var loadingOverlay: LinearLayout

    protected var overlayContainer: FrameLayout? = null
    protected var bookInfoOverlay: ScrollView? = null
    protected var bookTitle: EditableTextView? = null
    protected var bookAuthor: EditableTextView? = null
    protected var bookCategory: EditableCategoryView? = null
    protected var bookSubCategory: EditableCategoryView? = null
    protected var subCategoryListContainer: LinearLayout? = null
    protected var btnAddSubCategory: ImageButton? = null
    protected var bookPreviewImage: ImageView? = null
    protected var bookPreviewWrapper: FrameLayout? = null
    protected var isbnNumber: EditableTextView? = null
    protected var btnSearchCovers: ImageButton? = null
    protected var btnPickCover: ImageButton? = null
    protected var btnRevertCover: ImageButton? = null
    protected var btnFindReplace: ImageButton? = null
    protected var btnRedo: ImageButton? = null
    protected var addToHistory: Boolean = true
    protected var fullyCloseFromFileBrowser: Boolean = false
    protected val rootDir: File = Environment.getExternalStorageDirectory()
    protected val initialDir: File = File(rootDir, "Documents")
    protected var currentDir: File = File(rootDir, "Documents")
    protected var btnCloseGallery: ImageButton? = null
    protected var btnDeleteBook: ImageButton? = null
    protected var volumeTitleCheckbox: MaterialCheckBox? = null
    protected var coverOptionsRecycler: RecyclerView? = null
    protected var recipeImagePreviewWrapper: FrameLayout? = null
    protected var recipeImagePreviewTitle: TextView? = null
    protected var recipeImageBookTitle: TextView? = null
    protected var recipeImagePreview: ImageView? = null
    protected var closePreviewButton: ImageButton? = null
    var lastClickTime: Long = 0
    val DOUBLE_CLICK_TIME_DELTA: Long = 300

    private val drawerBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!checkCloseDrawerIsOpen()) {
                val currentKey = getDataStoreKey()
                addToHistory = false
                if (currentKey != null && !fullyCloseFromFileBrowser) {
                    lifecycleScope.launch {
                        val destination = dataStoreManager.popAndGetPrevious(currentKey)
                        if (destination != null) {
                            navigateBackToSavedActivity(destination)
                        } else {
                            handleBackPressLogic(false) {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        }
                    }
                } else {
                    handleBackPressLogic(false) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
    }

    protected fun handleClickToBuildPageImage(wrapper: FrameLayout, item: BaseBookmarkTocItem) {
        val content = wrapper.getChildAt(0)
        recipeImagePreviewTitle?.text = item.title
        recipeImageBookTitle?.text = item.bookTitle
        wrapper.visibility = View.VISIBLE
        wrapper.alpha = 0f
        content.scaleX = 0.8f
        content.scaleY = 0.8f
        wrapper.animate().alpha(1f).setDuration(200).start()
        content.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
        recipeImagePreview?.visibility = View.INVISIBLE
        buildPageImageIntoView(item)
    }

    protected fun checkCloseDrawerIsOpen(): Boolean {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        if (bookInfoOverlay?.visibility == View.VISIBLE) {
            hideBookInfoOverlay()
            return true
        }
        if (recipeImagePreviewWrapper?.visibility == View.VISIBLE) {
            hideRecipeImagePreview()
            return true
        }
        return false
    }

    private fun checkDataStoreEntries(callback: (Boolean) -> Unit) {
        val currentDataStoreKey = getDataStoreKey()
        lifecycleScope.launch {
            val count = dataStoreManager.getTotalEntries(currentDataStoreKey)
            callback(count > 0)
        }
    }

    protected fun handleBackPressLogic(
        callIfNotEmpty: Boolean = false,
        onConfirmToClose: () -> Unit
    ) {
        val currentDataStoreKey = getDataStoreKey()

        lifecycleScope.launch {
            val count = dataStoreManager.getTotalEntries(currentDataStoreKey)
            val hasResults = count > 0
            if (hasResults && callIfNotEmpty) {
                onConfirmToClose()
            }
            if (hasResults) {
                return@launch
            }
            if (!closeAppConfirmed) {
                if (callIfNotEmpty) {
                    fullyCloseFromFileBrowser = true
                }
                closeAppConfirmed = true
                displaySnackBarMessage(
                    "Click once more to close Recipe Book Reader",
                    drawerLayout,
                    true
                )
                delay(5000)
                closeAppConfirmed = false
                fullyCloseFromFileBrowser = false
            } else {
                onConfirmToClose()
            }
        }
    }

    protected fun navigateBackToSavedActivity(forcedDestination: HistoryEntry?) {
        addToHistory = false
        lifecycleScope.launch {
            var lastActivityName = dataStoreManager.lastActivityFlow.first()
            lastActivityName =
                getActivityNameFromDataStoreKey(forcedDestination?.keyName, lastActivityName)

            val simpleActivities = arrayOf(
                "apk.hurnell.recipebookreader.BookmarksActivity",
                "apk.hurnell.recipebookreader.BookShelfActivity",
                "apk.hurnell.recipebookreader.EveryTocActivity",
                "apk.hurnell.recipebookreader.RecentBooksActivity",
                "apk.hurnell.recipebookreader.FileBrowserActivity",
                "apk.hurnell.recipebookreader.RecentRecipesActivity",
                "apk.hurnell.recipebookreader.BookAuthorOrNameActivity",
            )

            if (lastActivityName == "apk.hurnell.recipebookreader.RecipeBookActivity") {
                val lastEntry = dataStoreManager.getLastHistoryEntry()
                val homeIntentClassName = getActivityNameFromDataStoreKey(
                    lastEntry?.keyName,
                    "apk.hurnell.recipebookreader.FileBrowserActivity"
                )
                val directory =
                    if (homeIntentClassName == "apk.hurnell.recipebookreader.FileBrowserActivity") {
                        lastEntry?.trackerJson?.let { json ->
                            try {
                                Gson().fromJson(json, FileBrowserTracker::class.java).directory
                            } catch (e: Exception) {
                                null
                            }
                        }
                    } else {
                        null
                    }
                if (directory != null) {
                    val file = File(directory)
                    if (file.exists()) {
                        currentDir = file
                    }
                }
                val homeIntentClass = Class.forName(homeIntentClassName!!)
                val homeIntent = Intent(this@BaseDrawerActivity, homeIntentClass).apply {
                    putExtra("STOP_ADD_TO_HISTORY", true)
                }
                startActivity(homeIntent)
                val success = navigateToSavedRecipeBookState()

                if (success) {
                    finish()
                    return@launch
                } else {
                    finish()
                    return@launch
                }
            }
            val targetClassName = if (lastActivityName in simpleActivities) {
                lastActivityName
            } else {
                "apk.hurnell.recipebookreader.FileBrowserActivity"
            }

            try {
                val targetClass = Class.forName(targetClassName!!)
                startActivity(Intent(this@BaseDrawerActivity, targetClass))
                finish()
            } catch (e: Exception) {
                startActivity(Intent(this@BaseDrawerActivity, FileBrowserActivity::class.java))
                finish()
            }
        }
    }

    protected fun buildPageImageIntoView(item: BaseBookmarkTocItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            var document: Document? = null
            var page: Page? = null
            var pixmap: Pixmap? = null

            try {
                val file = File(item.bookLocation!!)
                document = repository.openPdfFast(file)
                page = document.loadPage(item.page)

                var imageCount = 0
                var capturedImage: Image? = null

                val st = page.toStructuredText("preserve-images,preserve-whitespace")
                st.walk(object : StructuredTextWalker {
                    override fun onImageBlock(bbox: Rect, matrix: Matrix?, image: Image?) {
                        imageCount++
                        capturedImage = image
                    }

                    override fun beginTextBlock(bbox: Rect) {}
                    override fun endTextBlock() {}
                    override fun onChar(
                        c: Int,
                        origin: Point?,
                        font: Font?,
                        size: Float,
                        quad: Quad?,
                        argb: Int,
                        flags: Int
                    ) {
                    }

                    override fun beginLine(bbox: Rect?, wmode: Int, dir: Point?) {}
                    override fun endLine() {}
                    override fun beginStruct(standard: String?, raw: String?, index: Int) {}
                    override fun endStruct() {}
                    override fun onVector(
                        bbox: Rect?,
                        info: StructuredTextWalker.VectorInfo?,
                        argb: Int
                    ) {
                    }
                })

                if (imageCount == 1 && capturedImage != null) {
                    pixmap = capturedImage!!.toPixmap()
                    val width = pixmap.width
                    val height = pixmap.height

                    val rgbBytes = pixmap.samples
                    val rgbaBytes = ByteArray(width * height * 4)
                    for (i in 0 until (width * height)) {
                        rgbaBytes[i * 4 + 0] = rgbBytes[i * 3 + 0]
                        rgbaBytes[i * 4 + 1] = rgbBytes[i * 3 + 1]
                        rgbaBytes[i * 4 + 2] = rgbBytes[i * 3 + 2]
                        rgbaBytes[i * 4 + 3] = 255.toByte()
                    }

                    val bitmap = createBitmap(width, height)
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaBytes))
                    withContext(Dispatchers.Main) {
                        recipeImagePreview?.apply {
                            visibility = View.VISIBLE
                            setImageBitmap(bitmap)
                        }
                        recipeImagePreviewWrapper?.visibility = View.VISIBLE
                    }
                } else if (imageCount > 1 || imageCount == 0) {
                    renderWholePageAsFallback(page)
                }

            } catch (e: Exception) {
                renderWholePageAsFallback(page)
            } finally {
                pixmap?.destroy()
                page?.destroy()
                document?.destroy()
            }
        }
    }

    protected suspend fun renderWholePageAsFallback(page: Page?) {
        if (page == null) return

        val bounds = page.bounds
        val pageWidth = bounds.x1 - bounds.x0
        val displayDensity = resources.displayMetrics.density

        val targetBitmapWidth = (200 * displayDensity).toInt()
        val scale = targetBitmapWidth.toFloat() / pageWidth

        val bitmapHeight = ((bounds.y1 - bounds.y0) * scale).toInt()
        val bitmap = createBitmap(targetBitmapWidth, bitmapHeight)
        val device = AndroidDrawDevice(bitmap, 0, 0)

        try {
            page.run(device, Matrix(scale, scale), null)
        } finally {
            device.close()
            device.destroy()
        }
        withContext(Dispatchers.Main) {
            recipeImagePreview?.apply {
                visibility = View.VISIBLE
                setImageBitmap(bitmap)
            }
            recipeImagePreviewWrapper?.visibility = View.VISIBLE
        }
    }

    protected suspend fun getTracker(): RecipeBookTracker? {
        return try {
            dataStoreManager.recipeBookState.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    protected suspend fun navigateToSavedRecipeBookState(): Boolean {
        val tracker = getTracker()
        return if (tracker != null) {
            val intent = Intent(this@BaseDrawerActivity, RecipeBookActivity::class.java).apply {
                putExtra("PDF_PATH", tracker.location)
                val savedStateJson = Gson().toJson(tracker)
                putExtra("SAVED_STATE_JSON", savedStateJson)
            }
            addToHistory = false
            startActivity(intent)
            finish()
            true
        } else {
            false
        }
    }

    private fun getDataStoreKey(): String? {
        if (this::class.simpleName == "FileBrowserActivity") {
            return if (pdfOnly) "pdf_file_browser_tracker" else "image_file_browser_tracker"
        }
        return when (this::class.simpleName) {
            "BookmarksActivity" -> "bookmarks_tracker"
            "BookShelfActivity" -> "bookshelf_tracker"
            "EveryTocActivity" -> "every_toc_tracker"
            "RecentBooksActivity" -> "recent_books_tracker"
            "RecentRecipesActivity" -> "recent_recipes_tracker"
            "BookAuthorOrNameActivity" -> "book_author_or_name_tracker"
            else -> null
        }
    }

    private fun getActivityNameFromDataStoreKey(key: String?, fallback: String?): String? {
        return when (key) {
            "bookmarks_tracker" -> "apk.hurnell.recipebookreader.BookmarksActivity"
            "bookshelf_tracker" -> "apk.hurnell.recipebookreader.BookShelfActivity"
            "every_toc_tracker" -> "apk.hurnell.recipebookreader.EveryTocActivity"
            "pdf_file_browser_tracker" -> "apk.hurnell.recipebookreader.FileBrowserActivity"
            "image_file_browser_tracker" -> "apk.hurnell.recipebookreader.FileBrowserActivity"
            "recent_books_tracker" -> "apk.hurnell.recipebookreader.RecentBooksActivity"
            "recipe_book_tracker" -> "apk.hurnell.recipebookreader.RecipeBookActivity"
            "recent_recipes_tracker" -> "apk.hurnell.recipebookreader.RecentRecipesActivity"
            "book_author_or_name_tracker" -> "apk.hurnell.recipebookreader.BookAuthorOrNameActivity"
            else -> fallback
        }
    }

    companion object {
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockPortraitIfPhone()
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)
        repository = PdfRepository(this)

    }

    abstract fun refreshFilesAndUI(reloadAdapter: Boolean = false)

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        initBaseViews()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        initBaseViews()
    }

    protected fun refreshCategories() {
        val activityName = this::class.simpleName
        val usedCategories = repository.getUsedCategories(activityName, currentSearchTerm)
        categories.clear()
        usedCategories.forEach { item ->
            categories.add(item.category)
        }
        val activeColor = ContextCompat.getColor(this, R.color.nav_text)
        val disabledColor = ContextCompat.getColor(this, R.color.disabled_nav)

        val adapter = object : ArrayAdapter<CategoryItem>(
            this,
            android.R.layout.simple_spinner_item,
            usedCategories
        ) {
            override fun isEnabled(position: Int): Boolean {
                return getItem(position)?.count != 0
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getDropDownView(position, convertView, parent)
                val tv = view as TextView

                val item = getItem(position)
                val color = when (item?.count) {
                    0 -> disabledColor
                    else -> activeColor
                }
                tv.setTextColor(color)
                tv.text = item?.category ?: ""

                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.list_item_spinner_item)
        spinner.adapter = adapter
    }

    protected fun processAndOpenBook(
        pdfFile: File,
        bookmarkTocItem: BaseBookmarkTocItem? = null,
        recipeBookState: RecipeBookTracker? = null
    ) {
        if (bookmarkTocItem != null) {
            repository.addRecentRecipeItem(bookmarkTocItem)
        }
        loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bookmarkTocJson = bookmarkTocItem?.let { Gson().toJson(it) }
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    val intent =
                        Intent(this@BaseDrawerActivity, RecipeBookActivity::class.java).apply {
                            putExtra("PDF_PATH", pdfFile.absolutePath)
                            if (bookmarkTocJson != null) {
                                putExtra("BOOKMARK_TOC_ITEM_JSON", bookmarkTocJson)
                            }
                        }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    displaySnackBarMessage("Error opening PDF: ${e.message}", drawerLayout)
                }
            }
        }
    }

    private fun initBaseViews() {
        drawerLayout = findViewById(R.id.drawer_layout)

        overlayContainer = findViewById(R.id.overlayContainer)
        bookInfoOverlay = findViewById(R.id.bookInfoOverlay)
        bookPreviewImage = findViewById(R.id.bookPreviewImage)
        bookPreviewWrapper = findViewById(R.id.bookPreviewWrapper)
        isbnNumber = findViewById(R.id.isbnNumber)
        bookTitle = findViewById(R.id.bookTitle)
        bookAuthor = findViewById(R.id.bookAuthor)
        bookCategory = findViewById(R.id.bookCategory)
        bookSubCategory = findViewById(R.id.bookSubCategory)
        subCategoryListContainer = findViewById(R.id.subCategoryListContainer)
        btnAddSubCategory = findViewById(R.id.btnAddSubCategory)

        overlayContainer?.setOnClickListener { hideBookInfoOverlay() }

    }

    private val fileBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val updatedSha = result.data?.getStringExtra("updated_sha")
            refreshCoverForSha(updatedSha)
        }
    }

    fun revertCoverFromDocument(book: Book) {
        toggleOtherButtons(null, false)
        btnSearchCovers?.visibility = View.GONE
        btnPickCover?.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val file = File(book.location!!)
                val document = runCatching {
                    repository.openPdfFast(file)
                }.getOrElse {
                    return@launch
                }
                val success = repository.generateBookCoverThumbnail(
                    book.sha!!,
                    document,
                    book.name,
                    book.author,
                    200,
                    300,
                    true
                )
                document.destroy()
                if (success) {
                    withContext(Dispatchers.Main) {
                        setResetPreviewImage(book.sha, bookPreviewImage!!)
                    }
                }

                toggleOtherButtons(null, show = true, showRevertCover = false, sha = book.sha)
                btnSearchCovers?.visibility = View.VISIBLE
                btnPickCover?.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e(LOG_TAG, "${e.message}")
            }
        }

    }

    @SuppressLint("SourceLockedOrientationActivity")
    fun Activity.lockPortraitIfPhone() {
        val screenLayout =
            resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK

        val isTablet = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE

        if (!isTablet) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun searchFileSystemForBookCovers(book: Book) {
        val intent = Intent(this@BaseDrawerActivity, FileBrowserActivity::class.java).apply {
            putExtra(FileBrowserActivity.EXTRA_PDF_ONLY, false)
            putExtra(FileBrowserActivity.EXTRA_TARGET_SHA, book.sha)
            putExtra(FileBrowserActivity.EXTRA_TARGET_BOOK_NAME, book.name)
        }
        fileBrowserLauncher.launch(intent)
    }

    fun searchOrphanedCoversForBook(book: Book) {
        val intent = Intent(this@BaseDrawerActivity, FileBrowserActivity::class.java).apply {
            putExtra(FileBrowserActivity.EXTRA_ORPHANED_COVERS_ONLY, true)
            putExtra(FileBrowserActivity.EXTRA_TARGET_SHA, book.sha)
            putExtra(FileBrowserActivity.EXTRA_TARGET_BOOK_NAME, book.name)
        }
        fileBrowserLauncher.launch(intent)
    }

    private fun showRedoCoverConfirmation(book: Book) {
        val sha = book.sha ?: return
        lifecycleScope.launch {
            val bitmap = try {
                val file = File(book.location!!)
                val document = repository.openPdfFast(file)
                val rendered = repository.renderFirstPageCoverPreview(document)
                document.destroy()
                rendered
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error rendering first page preview: ${e.message}", e)
                null
            }

            if (bitmap == null) {
                displaySnackBarMessage("Could not generate a cover from the first page ❌", drawerLayout)
                return@launch
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_cover, null)
            val previewImageView = dialogView.findViewById<ImageView>(R.id.coverPreviewImage)
            previewImageView.setImageBitmap(bitmap)

            val dialog = MaterialAlertDialogBuilder(
                this@BaseDrawerActivity,
                R.style.ThemeOverlay_App_MaterialAlertDialog
            )
                .setTitle("Use this cover?")
                .setMessage("Replace the missing cover with the first page of the PDF?")
                .setView(dialogView)
                .setNegativeButton("Reject") { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton("Accept") { _, _ ->
                    saveBitmapAsCover(bitmap, sha)
                    repository.updateIsAlternateCover(sha, 0)
                    bookPreviewImage?.let { setResetPreviewImage(sha, it) }
                }
                .create()
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog.window?.setBackgroundDrawableResource(R.drawable.alert_background)
            dialog.show()
        }
    }

    fun showPossibleBookCovers(book: Book) {
        btnSearchCovers?.visibility = View.GONE
        toggleOtherButtons(null, false)

        btnPickCover?.visibility = View.GONE
        btnRevertCover?.visibility = View.GONE
        if (book.name != null && book.author != null) {
            lifecycleScope.launch {
                val urls = GetCoverUrlHelper().getAllAvailableCovers(book)
                if (urls.isNotEmpty()) {
                    bookPreviewImage?.visibility = View.GONE
                    bookPreviewWrapper?.visibility = View.GONE
                    coverOptionsRecycler?.visibility = View.VISIBLE
                    btnCloseGallery?.visibility = View.VISIBLE
                    btnSearchCovers?.visibility = View.GONE
                    toggleOtherButtons(null, false)
                    btnPickCover?.visibility = View.GONE
                    btnRevertCover?.visibility = View.GONE

                    coverOptionsRecycler?.adapter = CoverPickerAdapter(urls) { selectedUrl ->
                        coverOptionsRecycler?.visibility = View.GONE
                        bookPreviewImage?.visibility = View.VISIBLE
                        bookPreviewWrapper?.visibility = View.VISIBLE
                        btnCloseGallery?.visibility = View.GONE
                        btnSearchCovers?.visibility = View.VISIBLE
                        toggleOtherButtons(null, show = true, showRevertCover = true, sha = book.sha)
                        btnPickCover?.visibility = View.VISIBLE
                        repository.updateIsAlternateCover(book.sha!!, 1)
                        btnRevertCover?.visibility = View.VISIBLE
                        lifecycleScope.launch(Dispatchers.IO) {
                            val success = saveCoverAsPng(selectedUrl, book.sha)

                            if (success) {
                                withContext(Dispatchers.Main) {
                                    setResetPreviewImage(book.sha, bookPreviewImage!!)
                                }
                            }
                        }

                    }
                } else {

                    btnSearchCovers?.visibility = View.VISIBLE
                    btnPickCover?.visibility = View.VISIBLE
                    if (book.alternateCover) {
                        btnRevertCover?.visibility = View.VISIBLE
                    }
                    toggleOtherButtons(null, true, sha = book.sha)
                    displaySnackBarMessage("No thumbnail images found online ❌", drawerLayout)
                }
            }

        }
    }

    private fun saveCoverAsPng(url: String, sha: String): Boolean {
        return try {
            val originalBitmap = Glide.with(this)
                .asBitmap()
                .load(url)
                .submit()
                .get()
            val scaledBitmap = originalBitmap.scale(200, 300)
            saveBitmapAsCover(scaledBitmap, sha)
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error saving PNG for $sha", e)
            false
        }
    }

    protected fun saveBitmapAsCover(bitmap: Bitmap, sha: String): Boolean {
        return try {
            val hashName = "${sha}.png"
            val thumbnailFile = File(this.filesDir, hashName)
            FileOutputStream(thumbnailFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error writing bitmap to disk for $sha", e)
            false
        }
    }

    private fun updateCoverMissingButtonsVisibility(sha: String) {
        val exists = File(filesDir, "${sha}.png").exists()
        val visibility = if (exists) View.GONE else View.VISIBLE
        btnFindReplace?.visibility = visibility
        btnRedo?.visibility = visibility
    }

    private fun toggleOtherButtons(
        activeView: Any?,
        show: Boolean,
        showRevertCover: Boolean = false,
        sha: String? = null
    ) {
        btnSearchCovers?.visibility = if (show) View.VISIBLE else View.GONE
        btnPickCover?.visibility = if (show) View.VISIBLE else View.GONE
        btnRevertCover?.visibility = if (show && showRevertCover) View.VISIBLE else View.GONE
        btnAddSubCategory?.visibility = if (show) View.VISIBLE else View.GONE
        if (show && sha != null) {
            updateCoverMissingButtonsVisibility(sha)
        } else {
            btnFindReplace?.visibility = View.GONE
            btnRedo?.visibility = View.GONE
        }
        val buttons = listOf(bookTitle, bookAuthor, isbnNumber, bookCategory, bookSubCategory)
        buttons.forEach { btn ->
            if (btn != activeView) {
                btn?.toggleEditButton(show)
            }
        }
    }

    protected fun showBookInfoOverlay(
        pdfFile: File
    ) {

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = PdfRepository(this@BaseDrawerActivity)
                val book: Book = repository.getBook(
                    pdfFile.absolutePath
                ) ?: run {
                    Log.e(LOG_TAG, "Failed to get book")
                    finish()
                    return@launch
                }
                val previewImage = bookPreviewImage ?: run {
                    finish()
                    return@launch
                }


                btnRevertCover = findViewById(R.id.btnRevertCover)
                btnFindReplace = findViewById(R.id.btnFindReplace)
                btnRedo = findViewById(R.id.btnRedo)

                withContext(Dispatchers.Main) {
                    bookTitle?.setParams(book.id, book.name ?: pdfFile.name, "Title", Typeface.BOLD)
                    bookTitle?.onAccept { currentBookId, newText ->
                        if (currentBookId == book.id) {
                            repository.updateBookStringParam(book.id, "name", newText)
                        }
                    }
                    bookAuthor?.setParams(book.id, book.author?.ifBlank { "" } ?: "", "Author")
                    bookAuthor?.onAccept { currentBookId, newText ->
                        if (currentBookId == book.id) {
                            repository.updateBookStringParam(book.id, "author", newText)
                        }
                    }
                    isbnNumber?.setParams(book.id, book.isbn?.ifBlank { "" } ?: "", "ISBN")
                    isbnNumber?.onAccept { currentBookId, newText ->
                        if (currentBookId == book.id) {
                            repository.updateBookStringParam(book.id, "isbn", newText)
                        }
                    }
                    bookCategory?.setParams(book.id, repository, book.category, "Category")
                    bookCategory?.onAccept { currentBookId, name, categoryId ->
                        if (currentBookId == book.id) {
                            if (categoryId != null) {
                                repository.setMainCategory(book.id, categoryId)
                            } else if (name.isNotBlank()) {
                                val newId = repository.createCategory(name)
                                repository.setMainCategory(book.id, newId)
                                bookCategory?.setNewCategoryId(newId.toInt())
                            }
                        }
                    }
                    fun refreshSubCategoryChips() {
                        subCategoryListContainer?.removeAllViews()
                        repository.getSubCategories(book.id).forEach { subCategory ->
                            val rowBinding = ListItemSubCategoryBinding.inflate(
                                layoutInflater, subCategoryListContainer, false
                            )
                            rowBinding.subCategoryRowText.text = subCategory.category
                            rowBinding.subCategoryRowDelete.setOnClickListener {
                                repository.removeSubCategory(book.id, subCategory.id)
                                refreshSubCategoryChips()
                            }
                            subCategoryListContainer?.addView(rowBinding.root)
                        }
                    }
                    refreshSubCategoryChips()
                    bookSubCategory?.setParams(book.id, repository, null, null)
                    bookSubCategory?.setAddOnlyMode(true)
                    btnAddSubCategory?.setOnClickListener {
                        bookSubCategory?.beginEditing()
                    }
                    bookSubCategory?.onAccept { currentBookId, name, categoryId ->
                        if (currentBookId == book.id) {
                            val resolvedId = categoryId ?: if (name.isNotBlank()) {
                                repository.createCategory(name)
                            } else {
                                null
                            }
                            if (resolvedId != null) {
                                repository.addSubCategory(book.id, resolvedId)
                                bookSubCategory?.setText("")
                                refreshSubCategoryChips()
                            }
                        }
                    }
                    bookTitle?.onEditingChanged = { view, isEditing ->
                        toggleOtherButtons(view, !isEditing, sha = book.sha)
                    }
                    bookAuthor?.onEditingChanged = { view, isEditing ->
                        toggleOtherButtons(view, !isEditing, sha = book.sha)
                    }
                    isbnNumber?.onEditingChanged = { view, isEditing ->
                        toggleOtherButtons(view, !isEditing, sha = book.sha)
                    }
                    bookCategory?.onEditingChanged = { view, isEditing ->
                        toggleOtherButtons(view, !isEditing, sha = book.sha)
                    }
                    bookSubCategory?.onEditingChanged = { view, isEditing ->
                        toggleOtherButtons(view, !isEditing, sha = book.sha)
                    }
                    setResetPreviewImage(book.sha!!, previewImage)
                    overlayContainer?.visibility = View.VISIBLE
                    bookInfoOverlay?.scaleX = 0.8f
                    bookInfoOverlay?.scaleY = 0.8f
                    bookInfoOverlay?.alpha = 0f
                    bookInfoOverlay?.visibility = View.VISIBLE
                    if (book.alternateCover) {
                        btnRevertCover?.visibility = View.VISIBLE
                    }
                    bookInfoOverlay?.animate()
                        ?.alpha(1f)
                        ?.scaleX(1f)
                        ?.scaleY(1f)
                        ?.setDuration(250)
                        ?.start()
                }
                coverOptionsRecycler = findViewById(R.id.coverOptionsRecycler)
                btnSearchCovers = findViewById(R.id.btnSearchCovers)
                btnPickCover = findViewById(R.id.btnPickCover)
                btnCloseGallery = findViewById(R.id.btnCloseGallery)
                btnDeleteBook = findViewById(R.id.btnDeleteBook)
                volumeTitleCheckbox = findViewById(R.id.volumeTitleCheckbox)
                btnSearchCovers?.setOnClickListener {
                    showPossibleBookCovers(book)
                }
                btnPickCover?.setOnClickListener {
                    searchFileSystemForBookCovers(book)
                }
                btnRevertCover?.setOnClickListener {
                    revertCoverFromDocument(book)
                }
                btnFindReplace?.setOnClickListener {
                    searchOrphanedCoversForBook(book)
                }
                btnRedo?.setOnClickListener {
                    showRedoCoverConfirmation(book)
                }
                btnCloseGallery?.setOnClickListener {
                    coverOptionsRecycler?.visibility = View.GONE
                    btnCloseGallery?.visibility = View.GONE
                    bookPreviewImage?.visibility = View.VISIBLE
                    bookPreviewWrapper?.visibility = View.VISIBLE
                    btnSearchCovers?.visibility = View.VISIBLE
                    toggleOtherButtons(null, true, sha = book.sha)
                    btnPickCover?.visibility = View.VISIBLE
                }
                btnDeleteBook?.setOnClickListener {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
                    val checkBox =
                        dialogView.findViewById<MaterialCheckBox>(R.id.deleteFileCheckbox)

                    val dialog = MaterialAlertDialogBuilder(
                        this@BaseDrawerActivity,
                        R.style.ThemeOverlay_App_MaterialAlertDialog
                    )
                        .setTitle("Remove Book?")
                        .setMessage("Are you sure you want to remove this book?")
                        .setView(dialogView)
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setPositiveButton("Remove") { _, _ ->
                            val shouldDeleteFile = checkBox.isChecked
                            if (shouldDeleteFile) {
                                val location = book.location
                                if (location != null) {
                                    val bookFile = File(book.location)
                                    if (bookFile.exists()) {
                                        bookFile.delete()
                                    }
                                }
                            }
                            val success = repository.deleteBook(book.id)
                            if (success) {
                                hideBookInfoOverlay()
                                refreshFilesAndUI(true)
                            }
                        }
                        .create()
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        val positiveButton =
                            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        dialog.setTitle(if (isChecked) "Delete Book & File?" else "Remove Book?")
                        dialog.setMessage(if (isChecked) "Are you sure you want to delete this book?" else "Are you sure you want to remove this book?")
                        positiveButton.text = if (isChecked) "Delete" else "Remove"
                    }
                    dialog.window?.setLayout(
                        (resources.displayMetrics.widthPixels * 0.9).toInt(), // 90% of screen width
                        ViewGroup.LayoutParams.WRAP_CONTENT // height wraps content
                    )
                    dialog.window?.setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                    )
                    dialog.window?.setBackgroundDrawableResource(R.drawable.alert_background)
                    dialog.show()
                }
                volumeTitleCheckbox?.isChecked = book.volumeTitle
                volumeTitleCheckbox?.setOnCheckedChangeListener { _, isChecked ->
                    repository.updateVolumeTitleStatus(book.id, isChecked)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error opening PDF: ${e.message}", e)
            }
        }
    }

    protected fun displaySnackBarMessage(
        text: String,
        rootLayout: ViewGroup,
        isCloseWarning: Boolean = false
    ) {
        val duration = if (isCloseWarning) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG
        val snackBar = Snackbar.make(rootLayout, text, duration)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 5
        if (isCloseWarning) {
            snackBar.setDuration(5000)
        }
        snackBar.show()
    }

    protected fun refreshCoverForSha(updatedSha: String?) {
        if (updatedSha != null && bookPreviewImage != null) {

            setResetPreviewImage(updatedSha, bookPreviewImage!!, true)
            if (this is BookShelfActivity) {
                this.updateCoverForShaInAdapter(updatedSha)
            }
        }
    }

    private fun setResetPreviewImage(
        sha: String,
        previewImage: ImageView,
        isAlternateCover: Boolean = false
    ) {
        val thumbnailFile = File(
            previewImage.context.filesDir,
            "${sha}.png"
        )
        val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)

        if (bitmap != null) {
            previewImage.setImageBitmap(bitmap)
            bookPreviewImage?.setImageBitmap(bitmap)
        } else {
            previewImage.setImageResource(R.drawable.book_placeholder)
            bookPreviewImage?.setImageResource(R.drawable.book_placeholder)
        }
        updateCoverMissingButtonsVisibility(sha)
        if (isAlternateCover) {
            btnRevertCover?.visibility = View.VISIBLE
        }
    }

    protected fun hideBookInfoOverlay() {
        bookInfoOverlay?.animate()
            ?.alpha(0f)
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.setDuration(200)
            ?.withEndAction {
                bookInfoOverlay?.visibility = View.GONE
                overlayContainer?.visibility = View.GONE

                coverOptionsRecycler?.visibility = View.GONE
                bookPreviewImage?.visibility = View.VISIBLE
                bookPreviewWrapper?.visibility = View.VISIBLE
                btnCloseGallery?.visibility = View.GONE
                btnSearchCovers?.visibility = View.VISIBLE
                toggleOtherButtons(null, true)
                if (::spinner.isInitialized) {
                    refreshCategories()
                }
                this.refreshFilesAndUI(true)
            }
            ?.start()
    }

    private fun closeKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    protected fun setupDrawer(toolbar: Toolbar) {
        drawerLayout = findViewById(R.id.drawer_layout)
        setSupportActionBar(toolbar)
        window.decorView.setBackgroundColor(getColor(R.color.pastel_blue))

        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerStateChanged(newState: Int) {
                if (newState != DrawerLayout.STATE_IDLE) {
                    closeKeyboard()
                }
            }
        })
        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, R.color.nav_text)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        for (i in 0 until toolbar.childCount) {
            val v = toolbar.getChildAt(i)
            if (v is TextView) {
                v.setOnClickListener {
                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                        onToolbarDoubleClick()
                    }
                    lastClickTime = clickTime
                }
            } else if (v is LinearLayout && v.tag == "fileBrowserLayoutTag") {
                for (j in 0 until v.childCount) {
                    val lv = v.getChildAt(j)
                    if (lv is TextView) {
                        lv.setOnClickListener {
                            val clickTime = System.currentTimeMillis()
                            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                                onToolbarDoubleClick()
                            }
                            lastClickTime = clickTime
                        }
                    }
                }
            }
        }
    }

    private fun onToolbarDoubleClick() {
        lifecycleScope.launch {
            dataStoreManager.logFullHistorySafely()
        }
    }

    protected fun hideRecipeImagePreview() {
        recipeImagePreviewWrapper?.visibility = View.GONE
    }

}