package apk.hurnell.recipebookreader

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.BookAdapter
import apk.hurnell.recipebookreader.databinding.ActivityRecipeBookBinding
import apk.hurnell.recipebookreader.helpers.FunctionalStructuredTextWalker
import apk.hurnell.recipebookreader.ui.PinchRecyclerView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.TocItem
import apk.hurnell.recipebookreader.ui.TocFragment
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Link
import com.google.gson.Gson
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.*
import kotlin.math.floor
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import apk.hurnell.recipebookreader.helpers.Coordinates
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.helpers.IsbnFinder
import apk.hurnell.recipebookreader.model.BaseBookmarkTocItem
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.BookHistoryItem
import apk.hurnell.recipebookreader.model.BookmarkItem
import apk.hurnell.recipebookreader.ui.TocFragmentListener
import com.artifex.mupdf.fitz.Rect
import com.google.android.material.textfield.TextInputEditText

data class RecipeBookTracker(
    val portrait: Boolean?,
    val location: String?,
    val pageIndex: Int?,
    val offset: Int?,
    val translationX: Float?,
    val scale: Float?
) : BaseTracker()


class RecipeBookActivity : AppCompatActivity(), TocFragmentListener {

    private lateinit var binding: ActivityRecipeBookBinding

    private lateinit var history: List<BookHistoryItem>
    private lateinit var tocFragment: TocFragment
    private lateinit var bookLocation: String
    private lateinit var repository: PdfRepository
    private var bottomInset: Int = 0
    private var originalStatusBarHeight: Int = 0
    private lateinit var systemBars: Insets
    private var isPortrait = true
    private var barsVisible = true
    private var linkState: Int = 0
    private var totalPages = 0
    private var document: Document? = null
    private var triedToClose: Boolean = false
    private var currentBookId: Long = -1L
    private var scanned: Boolean = false
    private var clearSearchMenuItem: MenuItem? = null
    private var isbnScanJob: Job? = null

    companion object {
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                getColor(R.color.pastel_blue), getColor(R.color.pastel_blue)
            )
        )
        binding = ActivityRecipeBookBinding.inflate(layoutInflater)
        setContentView(binding.drawerLayout)

        val bookmarkTocJson = intent.getStringExtra("BOOKMARK_TOC_ITEM_JSON")
        val bookmarkTocItem = if (bookmarkTocJson != null) {
            Gson().fromJson(bookmarkTocJson, TocItem::class.java)
        } else {
            null
        }
        val savedStateJson = intent.getStringExtra("SAVED_STATE_JSON")
        val recipeBookTracked = if (savedStateJson != null) {
            Gson().fromJson(savedStateJson, RecipeBookTracker::class.java)
        } else {
            null
        }
        setupWindowInsets()
        setupSystemBars()

        setupStaticListeners()

        val pdfFilePath = intent.getStringExtra("PDF_PATH")
        if (pdfFilePath == null) {
            Log.e(LOG_TAG, "No PDF path provided")
            finish()
            return
        }
        bookLocation = pdfFilePath
        val pdfFile = File(pdfFilePath)
        binding.btnShowToc.visibility = View.GONE
        repository = PdfRepository(this)
        lifecycleScope.launch {
            try {
                val currentDocument = runCatching {
                    repository.openPdfFast(pdfFile)
                }.getOrElse {
                    Log.e(LOG_TAG, "Failed to open document", it)
                    finish()
                    return@launch
                }
                document = currentDocument
                onDocumentReady(currentDocument, bookmarkTocItem, recipeBookTracked)

                val book =
                    repository.getOrCreateBook(pdfFile, pdfFilePath, currentDocument) ?: run {
                        Log.e(LOG_TAG, "Failed to create or fetch book")
                        finish()
                        return@launch
                    }

                val bookId = book.id
                currentBookId = bookId
                if (bookId == -1L) {
                    Log.e(LOG_TAG, "Invalid bookId returned")
                    finish()
                    return@launch
                }
                binding.recipeBookToolbar.menu
                    .findItem(R.id.action_search)
                    ?.isVisible = !book.scanned
                loadBookHistory(bookId)
                if (!book.name.isNullOrEmpty()) {
                    binding.recipeBookToolbar.title = book.name
                }
                if (repository.hasToc(bookId)) {
                    initializeTocFragment(bookId)
                    binding.btnShowToc.visibility = View.VISIBLE
                } else {
                    Log.d(LOG_TAG, "Generating TOC in background...")
                    binding.horizontalLoader.visibility = View.VISIBLE
                    binding.horizontalLoader.progress = 0

                    val success = withContext(Dispatchers.IO) {
                        val tocSuccess = repository.generateTocAsync(
                            currentDocument, bookId, progressCallback = { percent: Int ->
                                lifecycleScope.launch(Dispatchers.Main) {
                                    binding.horizontalLoader.progress = percent
                                    if (percent >= 100) {
                                        binding.horizontalLoader.visibility = View.GONE
                                        binding.btnShowToc.visibility = View.VISIBLE
                                    }
                                }
                            })

                        tocSuccess
                    }
                    isbnScanJob = lifecycleScope.launch(Dispatchers.IO) {
                        val document = repository.openPdfFast(pdfFile)
                        try {
                            val foundIsbn = IsbnFinder().findIsbnInDocument(document)
                            repository.updateBookIsbn(bookId, foundIsbn)
                        } finally {
                            document.destroy()
                        }
                    }


                    if (success) {
                        withContext(Dispatchers.Main) {
                            binding.horizontalLoader.visibility = View.GONE
                            binding.btnShowToc.visibility = View.VISIBLE
                            initializeTocFragment(bookId)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error loading PDF", e)
                finish()
            }
        }
        binding.recipeBookToolbar.inflateMenu(R.menu.recipe_book_menu)

        binding.recipeBookToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.putExtra("PDF_PATH", bookLocation)
                    searchLauncher.launch(intent)
                    true
                }

                R.id.action_clear_search -> {
                    clearSearchHighlights()
                    true
                }

                else -> false
            }
        }

    }

    fun setStatusBarColor(window: Window, color: Int) {
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsets.Type.statusBars())
            view.setBackgroundColor(color)

            view.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val pageIndex = result.data?.getIntExtra("SELECTED_PAGE", -1) ?: -1
            val rectanglesJson = result.data?.getStringExtra("BLOCK_RECTANGLES_JSON")
            val coordinatesJson = result.data?.getStringExtra("PAGE_COORDINATES")
            if (pageIndex != -1 && rectanglesJson != null) {
                val gson = Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<Rect>>() {}.type
                val rectangles: List<Rect> = gson.fromJson(rectanglesJson, type)
                val coordinatesType =
                    object : com.google.gson.reflect.TypeToken<Coordinates>() {}.type
                val coordinates: Coordinates = gson.fromJson(coordinatesJson, coordinatesType)

                (binding.bookRecyclerView.adapter as? BookAdapter)?.setHighlight(
                    pageIndex, rectangles
                )
                binding.bookRecyclerView.scrollToPosition(pageIndex)
                binding.bookRecyclerView.setScaleFactor(
                    coordinates.scale, pageIndex, coordinates.percentage
                )
                updatePageText(pageIndex, totalPages)
                binding.pageSeekBar.progress = pageIndex

                toggleBars(true)

                if (clearSearchMenuItem == null) {
                    clearSearchMenuItem =
                        binding.recipeBookToolbar.menu.findItem(R.id.action_clear_search)
                }
                clearSearchMenuItem?.isVisible = true

                toggleBars(false)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.recipe_book_menu, menu)
        clearSearchMenuItem = menu.findItem(R.id.action_clear_search)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_search -> {
                clearSearchHighlights()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun clearSearchHighlights() {
        val adapter = binding.bookRecyclerView.adapter as? BookAdapter
        adapter?.clearHighlight()

        clearSearchMenuItem?.isVisible = false
    }

    private fun loadBookHistory(bookId: Long) {
        history = repository.getBookHistory(bookId)
    }

    private fun buildTracker(): RecipeBookTracker {
        val pinch = binding.bookRecyclerView

        return RecipeBookTracker(
            portrait = isPortrait,
            location = bookLocation,
            pageIndex = binding.pageSeekBar.progress,
            offset = pinch.computeVerticalScrollOffset(),
            translationX = pinch.translationX,
            scale = pinch.getScaleFactor()
        )
    }

    private fun initializeTocFragment(id: Long) {
        tocFragment = TocFragment.newInstance(id.toInt()) { item ->
            binding.bookRecyclerView.scrollToPosition(item.page)
            val historyItem = binding.bookRecyclerView.setScaleFactor(
                item.scale.coerceAtMost(3.0f), item.page, item.translate
            )
            binding.bookRecyclerView.post {
                historyItem.bookId = currentBookId
                var actualOffset = binding.bookRecyclerView.computeVerticalScrollOffset()
                if (item.offset != null) {
                    val requestedOffset = item.offset
                    val diff = requestedOffset?.minus(actualOffset)
                    if (diff != 0) {
                        binding.bookRecyclerView.scrollBy(0, diff!!)
                    }
                    actualOffset = requestedOffset
                }
                historyItem.offset = actualOffset
                history = repository.addBookHistoryItem(historyItem)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            toggleBars(false)
            updatePageText(item.page, totalPages)
        }
        supportFragmentManager.beginTransaction().replace(R.id.tocFragmentContainer, tocFragment)
            .commit()
    }

    private fun onDocumentReady(
        doc: Document, bookmarkTocItem: BaseBookmarkTocItem?, recipeBookTracked: RecipeBookTracker?
    ) {
        val metrics = resources.displayMetrics
        val horizontalBars = if (::systemBars.isInitialized) systemBars.right else 0
        val initialUw = metrics.widthPixels - horizontalBars
        val adapter = BookAdapter(doc, initialUw)
        binding.bookRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.bookRecyclerView.adapter = adapter

        totalPages = doc.countPages()
        binding.pageSeekBar.max = if (totalPages > 0) totalPages - 1 else 0
        var currentPage = 0
        if (bookmarkTocItem != null) {
            currentPage = bookmarkTocItem.page
        }
        updatePageText(currentPage, totalPages)

        setupRecyclerViewTouchListener()
        if (bookmarkTocItem != null) {
            val pinchRv = binding.bookRecyclerView
            pinchRv.scrollToPosition(bookmarkTocItem.page)
            binding.bookRecyclerView.setScaleFactor(
                bookmarkTocItem.scale.coerceAtMost(3.0f),
                bookmarkTocItem.page,
                bookmarkTocItem.translate
            )
            toggleBars(false)
            updatePageText(bookmarkTocItem.page, totalPages)
            if (bookmarkTocItem.offset != null) {
                binding.bookRecyclerView.post {
                    val requestedOffset = bookmarkTocItem.offset
                    val actualOffset = binding.bookRecyclerView.computeVerticalScrollOffset()
                    val diff = requestedOffset?.minus(actualOffset)

                    if (diff != 0) {
                        binding.bookRecyclerView.scrollBy(0, diff!!)
                    }
                }
            }
        }

        recipeBookTracked?.let { tracker ->
            binding.bookRecyclerView.handleReturnToRecipeBookTracker(tracker)
            binding.bookRecyclerView.post {
                val requestedOffset = tracker.offset
                val actualOffset = binding.bookRecyclerView.computeVerticalScrollOffset()
                val diff = requestedOffset?.minus(actualOffset)

                if (diff != 0) {
                    binding.bookRecyclerView.scrollBy(0, diff!!)
                }
            }
        }

    }

    private fun saveBookmark(bookmark: BookmarkItem) {
        repository.createBookmark(bookmark)
        tocFragment.loadBookmarksAsync(false)
        onBookmarkDataReloaded(false)
    }

    private fun setupRecyclerViewTouchListener() {
        binding.bookRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {

                val currentDoc = document ?: return false

                val pinchRv = rv as? PinchRecyclerView ?: return false
                val child: View? = rv.findChildViewUnder(e.x, e.y)

                if (child != null && e.action == MotionEvent.ACTION_DOWN) {
                    val touchContext = pinchRv.touchMetadata[e.eventTime]
                    val holder = rv.getChildViewHolder(child) as? BookAdapter.PageViewHolder

                    val pageScale = holder?.pageScale ?: 1f
                    val pageHeight = holder?.pageHeight ?: 100
                    val pageWidth = holder?.pageWidth ?: 100.0f

                    var px = 0f
                    var py = 0f
                    var pagePosition = 0

                    if (touchContext != null) {
                        px = touchContext.pageX / pageScale
                        py = touchContext.pageY / pageScale
                        pagePosition = floor((py * 100) / pageHeight).toInt()
                        py = ((py * 100) % pageHeight) / 100
                        Log.i(LOG_TAG, "px $px py $py page $pagePosition")
                    }

                    toggleBars(false)

                    lifecycleScope.launch {
                        val initialOffset = touchContext?.offset ?: 0
                        val initialTransX = touchContext?.translationX ?: 0f
                        val initialScale = touchContext?.scaleFactor ?: 1f

                        delay(200L)

                        val hasMoved =
                            abs(pinchRv.computeVerticalScrollOffset() - initialOffset) > 5 || abs(
                                pinchRv.translationX - initialTransX
                            ) > 5 || abs(pinchRv.getScaleFactor() - initialScale) > 0.01f

                        if (!hasMoved) {
                            if (linkState == 2) {
                                val text = getTextNearClickPoint(
                                    document, pagePosition, px, py
                                )
                                val o = pinchRv.computeVerticalScrollOffset()
                                Log.i("NIGEL_HURNELL", "Offset is $o")
                                val dialogView =
                                    layoutInflater.inflate(R.layout.dialog_bookmark, null)
                                val editText =
                                    dialogView.findViewById<TextInputEditText>(R.id.enterBookmarkText)
                                editText.setText(text)
                                val currentBookmarkItem = BookmarkItem(
                                    tocId = null,
                                    bookmarkId = null,
                                    title = "",
                                    bookTitle = null,
                                    bookLocation,
                                    bookId = currentBookId,
                                    page = pagePosition,
                                    offset = pinchRv.computeVerticalScrollOffset(),
                                    scale = pinchRv.getScaleFactor(),
                                    translate = pinchRv.getTranslate()
                                )
                                val dialog = AlertDialog.Builder(this@RecipeBookActivity)
                                    .setTitle("Create Bookmark")
                                    .setView(dialogView)
                                    .setPositiveButton("Create Bookmark") { _, _ ->
                                        val bookmarkText = editText.text.toString()

                                        if (bookmarkText.isNotBlank()) {
                                            currentBookmarkItem.title = bookmarkText
                                            saveBookmark(
                                                currentBookmarkItem
                                            )
                                        } else {
                                            Toast.makeText(
                                                this@RecipeBookActivity,
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
                            } else if (!checkIfTopOfPageClicked(
                                    currentDoc, pinchRv, px, py, pageWidth, pagePosition
                                )
                            ) {
                                checkFollowLinks(
                                    pinchRv, px, py, pageWidth, currentDoc, pagePosition
                                )
                            }
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun setupStaticListeners() {
        binding.recipeBookToolbar.setNavigationOnClickListener { finish() }

        binding.btnRotate.setOnClickListener {
            isPortrait = !isPortrait
            requestedOrientation = if (isPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            if (isPortrait) {
                binding.btnRotate.setImageResource(R.drawable.ic_to_landscape)
                binding.zoomIt.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = 0
                }
                binding.bookRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = 0
                }
            } else {
                binding.btnRotate.setImageResource(R.drawable.ic_to_portrait)
                binding.zoomIt.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = bottomInset
                }
                binding.bookRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = bottomInset
                }
            }
        }

        binding.btnShowToc.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            tocFragment.setShowingToc(true)
            toggleBars(false)
        }

        binding.bottomBar.setOnClickListener {

        }

        binding.btnShowBookmarks.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            tocFragment.setShowingToc(false)
            toggleBars(false)
        }

        binding.btnCloseApp.setOnClickListener {
            if (!triedToClose) {
                Toast.makeText(
                    this@RecipeBookActivity,
                    "Click once more to close Recipe Book Reader",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                moveTaskToBack(true)
            }

            triedToClose = true
        }

        binding.btnBackInHistory.setOnClickListener {
            if (history.isNotEmpty()) {
                val latestHistoryItem = history.first()
                binding.bookRecyclerView.handleReturnToRecipeBookHistoryItem(latestHistoryItem)
                binding.bookRecyclerView.post {
                    val requestedOffset = latestHistoryItem.offset
                    val actualOffset = binding.bookRecyclerView.computeVerticalScrollOffset()
                    val diff = requestedOffset?.minus(actualOffset)
                    if (diff != 0) {
                        binding.bookRecyclerView.scrollBy(0, diff!!)
                    }
                    history =
                        repository.removeBookHistoryItem(latestHistoryItem.id!!, currentBookId)
                }
            } else {
                finish()
            }
        }

        binding.zoomIt.setOnClickListener {
            toggleBars(!barsVisible)
        }
        val color = ContextCompat.getColor(this, R.color.nav_text)
        binding.zoomIt.imageTintList = ColorStateList.valueOf(color)
        binding.stopLinks.setOnClickListener {
            toggleLinks()
        }

        binding.pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.bookRecyclerView.scrollToPosition(progress)
                    updatePageText(progress, document?.countPages() ?: 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.bookRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val currentPosition = layoutManager.findFirstVisibleItemPosition()
                binding.pageSeekBar.progress = currentPosition
                if (barsVisible && abs(dy) > 10) toggleBars(false)
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (!barsVisible) {
                    toggleBars(true)
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)

        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setStatusBarColor(window, getColor(R.color.transparent))
    }

    private fun handleExternalLink(uri: String?) {

    }

    private fun handleInternalLink(rv: PinchRecyclerView, link: Link, w: Float) {
        val uri = link.uri
        val fragment = uri.substring(1)
        val params = fragment.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var page = 0
        for (param in params) {
            if (param.startsWith("page=")) {
                page = param.substring(5).toInt()
            }

            if (param.startsWith("zoom=")) {
                val zoomParts =
                    param.substring(5).split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()

                if (zoomParts.size >= 3) {
                    val xOffset = zoomParts[1].toFloat()
                    val zoom = (w / (w - 2 * xOffset) * 0.95f)
                    val historyItem = rv.setScaleFactor(zoom, page - 1, 0.5f)
                    toggleBars(false)
                    updatePageText(page - 1, totalPages)
                    binding.bookRecyclerView.post {
                        historyItem.bookId = currentBookId
                        historyItem.offset = binding.bookRecyclerView.computeVerticalScrollOffset()
                        history = repository.addBookHistoryItem(historyItem)
                    }
                }
            }
        }
    }

    private fun checkFollowLinks(
        rv: PinchRecyclerView, x: Float, y: Float, w: Float, document: Document?, pageNumber: Int
    ) {
        if (document == null) return
        val page = document.loadPage(pageNumber)
        val links = page.links
        if (links == null) {
            page.destroy()
            return
        }
        for (link in links) {
            val rect = link.bounds
            if (x >= rect.x0 && x <= rect.x1 && y >= rect.y0 && y <= rect.y1) {

                if (link.isExternal) {
                    val uri = link.uri
                    if (linkState == 0) {
                        handleExternalLink(uri)
                    }
                } else {
                    page.destroy()
                    if (linkState == 0) {
                        handleInternalLink(rv, link, w)
                    }
                }
                break
            }
        }
    }

    private fun getTextNearClickPoint(
        document: Document?, currentPage: Int, x: Float, y: Float
    ): String {
        if (document == null) {
            return ""
        }
        val page = document.loadPage(currentPage)
        val structuredText = page.toStructuredText()
        structuredText.blocks?.forEach { block ->
            block.lines?.forEach { line ->
                val bbox = line.bbox
                val xHit = bbox.x0 <= x && bbox.x1 >= x
                val yHit = bbox.y0 <= y && bbox.y1 >= y
                if (xHit && yHit) {
                    val lineBuilder = StringBuilder()
                    line.chars?.forEach { char -> lineBuilder.append(char.c.toChar()) }
                    return lineBuilder.toString().trim()
                }
            }
        }
        return ""
    }

    private fun checkIfTopOfPageClicked(
        document: Document?,
        rv: PinchRecyclerView,
        x: Float,
        y: Float,
        width: Float,
        currentPage: Int
    ): Boolean {
        if (linkState != 0) {
            return true
        }
        if (document == null) {
            return false
        }
        val margin = 30f
        val offset = width / 2 - x
        val hitX = abs(offset) < margin
        val hitY = y < margin * 2
        if (!hitY || !hitX) {
            return false
        }
        val pageCoordinates =
            FunctionalStructuredTextWalker().getPageCoordinates(document, currentPage)
        if (pageCoordinates.found) {
            val finalScale = pageCoordinates.targetScale.coerceAtMost(3.0f)
            val historyItem =
                rv.setScaleFactor(finalScale, currentPage, pageCoordinates.translatingPercentage)
            toggleBars(false)
            updatePageText(currentPage, totalPages)
            binding.bookRecyclerView.post {
                historyItem.bookId = currentBookId
                historyItem.offset = binding.bookRecyclerView.computeVerticalScrollOffset()
                history = repository.addBookHistoryItem(historyItem)
            }
        }
        return pageCoordinates.found
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val actionBarHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics
            ).toInt()
            val metrics = resources.displayMetrics
            val currentUw = metrics.widthPixels
            (binding.bookRecyclerView.adapter as? BookAdapter)?.updateUsableWidth(currentUw)
            bottomInset = systemBars.bottom.coerceAtLeast(ime.bottom)
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                bottomInset
            )
            binding.zoomIt.setPadding(
                binding.zoomIt.paddingLeft,
                binding.zoomIt.paddingTop,
                binding.zoomIt.paddingRight,
                bottomInset + binding.bottomBar.height
            )
            if (systemBars.top != 0) {
                originalStatusBarHeight = systemBars.top
            }
            binding.recipeBookToolbar.layoutParams.height =
                actionBarHeight + systemBars.top + originalStatusBarHeight
            binding.recipeBookToolbar.setPadding(
                binding.recipeBookToolbar.paddingLeft,
                originalStatusBarHeight,
                binding.recipeBookToolbar.paddingRight,
                binding.recipeBookToolbar.paddingBottom
            )
            binding.tocPanel.setPadding(0, 0, 0, bottomInset)
            binding.zoomIt.setPadding(0, 0, 0, bottomInset)

            insets
        }
    }

    private fun updatePageText(current: Int, total: Int) {
        binding.pageIndicator.text = getString(R.string.page_indicator, current + 1, total)
    }

    private fun toggleLinks() {
        linkState = (linkState + 1) % 3
        when (linkState) {
            0 -> {
                val color = ContextCompat.getColor(this, R.color.links_working)
                binding.stopLinks.imageTintList = ColorStateList.valueOf(color)
                binding.stopLinks.setImageResource(R.drawable.ic_link_on)
            }

            1 -> {
                val colorOff = ContextCompat.getColor(this, R.color.links_off)
                binding.stopLinks.imageTintList = ColorStateList.valueOf(colorOff)
                binding.stopLinks.setImageResource(R.drawable.ic_link_off)
            }

            else -> {
                val colorOff = ContextCompat.getColor(this, R.color.links_working)
                binding.stopLinks.imageTintList = ColorStateList.valueOf(colorOff)
                binding.stopLinks.setImageResource(R.drawable.ic_bookmark_open)
            }
        }

    }

    private fun toggleBars(show: Boolean) {
        if (barsVisible == show) return
        barsVisible = show
        val imeHeight = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val translationTop = if (show) 0f else -binding.recipeBookToolbar.height.toFloat()
        val translationBottom = if (show) 0f else binding.bottomBar.height.toFloat() - imeHeight

        binding.recipeBookToolbar.animate().translationY(translationTop).setDuration(300).start()
        binding.bottomBar.animate().translationY(translationBottom).setDuration(300).start()
        binding.btnRotate.animate().translationY(translationBottom).setDuration(300).start()
        binding.zoomIt.animate().translationY(translationBottom).setDuration(300).start()
        binding.stopLinks.animate().translationY(translationBottom).setDuration(300).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.clearBookHistory(currentBookId)
        document?.destroy()
    }

    override fun onPause() {
        super.onPause()
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@RecipeBookActivity::class.java.name)
            val currentTracker = buildTracker()
            dataStoreManager.saveTracker(DataStoreManager.RECIPE_BOOK_KEY, currentTracker)
        }
    }

    override fun onBookmarkDataReloaded(isEmpty: Boolean) {

        binding.btnShowBookmarks.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}