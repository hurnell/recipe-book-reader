package apk.hurnell.recipebookreader

import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
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
import androidx.core.content.ContextCompat
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
import apk.hurnell.recipebookreader.helpers.IsbnFinder


class RecipeBookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeBookBinding
    private lateinit var tocFragment: TocFragment
    private var isPortrait = true
    private var barsVisible = true
    private var linksWorking = true
    private var totalPages = 0
    private var document: Document? = null
    private lateinit var repository: PdfRepository

    companion object {
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                getColor(R.color.pastel_blue),
                getColor(R.color.pastel_blue)
            )
        )
        binding = ActivityRecipeBookBinding.inflate(layoutInflater)
        setContentView(binding.drawerLayout)

        setupWindowInsets()
        setupSystemBars()

        setupStaticListeners()

        val pdfFilePath = intent.getStringExtra("PDF_PATH")
        if (pdfFilePath == null) {
            Log.e(LOG_TAG, "No PDF path provided")
            finish()
            return
        }
        val tocJson = intent.getStringExtra("TOC_ITEM_JSON")
        val tocItem = if (tocJson != null) {
            Gson().fromJson(tocJson, TocItem::class.java)
        } else {
            null
        }
        val pdfFile = File(pdfFilePath)
        binding.btnShowToc.visibility = View.GONE
        repository = PdfRepository(this)

        lifecycleScope.launch {
            try {
                // Open PDF
                val currentDocument = runCatching {
                    repository.openPdfFast(pdfFile)
                }.getOrElse {
                    Log.e(LOG_TAG, "Failed to open document", it)
                    finish()
                    return@launch
                }
                document = currentDocument
                onDocumentReady(currentDocument, tocItem)

                // Get or create book in DB
                val book = repository.getOrCreateBook(pdfFile, pdfFilePath, currentDocument)
                    ?: run {
                        Log.e(LOG_TAG, "Failed to create or fetch book")
                        finish()
                        return@launch
                    }

                val bookId = book.id
                if (bookId == -1L) {
                    Log.e(LOG_TAG, "Invalid bookId returned")
                    finish()
                    return@launch
                }

                if (!book.name.isNullOrEmpty()) {
                    binding.toolbar.title = book.name
                }

                // Check TOC
                if (repository.hasToc(bookId)) {
                    initializeTocFragment(bookId)
                    binding.btnShowToc.visibility = View.VISIBLE
                } else {
                    Log.d(LOG_TAG, "Generating TOC in background...")
                    binding.horizontalLoader.visibility = View.VISIBLE
                    binding.horizontalLoader.progress = 0

                    // Use IO dispatcher for heavy DB work
                    val success = withContext(Dispatchers.IO) {
                        val tocSuccess = repository.generateTocAsync(
                            currentDocument,
                            bookId,
                            progressCallback = { percent: Int ->
                                // Update UI safely on Main
                                lifecycleScope.launch(Dispatchers.Main) {
                                    binding.horizontalLoader.progress = percent
                                    if (percent >= 100) {
                                        binding.horizontalLoader.visibility = View.GONE
                                        binding.btnShowToc.visibility = View.VISIBLE
                                    }
                                }
                            }
                        )
                        val foundIsbn = IsbnFinder().findIsbnInDocument(currentDocument)
                        if (foundIsbn != null) {
                            repository.updateBookIsbn(bookId, foundIsbn)
                            Log.d(LOG_TAG, "Found and updated ISBN: $foundIsbn")
                        }
                        tocSuccess
                    }

                    if (success) {
                        // Ensure UI updates happen on Main thread
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
    }

    private fun initializeTocFragment(id: Long) {
        tocFragment = TocFragment.newInstance(id.toInt()) { item ->
            binding.bookRecyclerView.scrollToPosition(item.page)
            binding.bookRecyclerView.setScaleFactor(
                item.scale.coerceAtMost(3.0f),
                item.page,
                item.translate
            )
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            toggleBars(false)
            updatePageText(item.page, totalPages)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.tocFragmentContainer, tocFragment)
            .commit()
    }

    private fun onDocumentReady(doc: Document, tocItem: TocItem?) {
        val adapter = BookAdapter(doc)
        binding.bookRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.bookRecyclerView.adapter = adapter

        totalPages = doc.countPages()
        binding.pageSeekBar.max = if (totalPages > 0) totalPages - 1 else 0
        var currentPage = 0
        if (tocItem != null) {
            currentPage = tocItem.page
        }
        updatePageText(currentPage, totalPages)

        setupRecyclerViewTouchListener()
        if (tocItem != null) {
            binding.bookRecyclerView.scrollToPosition(tocItem.page)
            binding.bookRecyclerView.doOnNextLayout {
                binding.bookRecyclerView.setScaleFactor(
                    tocItem.scale.coerceAtMost(3.0f),
                    tocItem.page,
                    tocItem.translate
                )
                toggleBars(false)
                updatePageText(tocItem.page, totalPages)
            }
        }

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
                    }

                    toggleBars(false)

                    lifecycleScope.launch {
                        val initialOffset = touchContext?.offset ?: 0
                        val initialTransX = touchContext?.translationX ?: 0f
                        val initialScale = touchContext?.scaleFactor ?: 1f

                        delay(200L)

                        val hasMoved =
                            abs(pinchRv.computeVerticalScrollOffset() - initialOffset) > 5 ||
                                    abs(pinchRv.translationX - initialTransX) > 5 ||
                                    abs(pinchRv.getScaleFactor() - initialScale) > 0.01f

                        if (!hasMoved) {
                            if (!checkIfTopOfPageClicked(
                                    currentDoc,
                                    pinchRv,
                                    px,
                                    py,
                                    pageWidth,
                                    pagePosition
                                )
                            ) {
                                checkFollowLinks(
                                    pinchRv,
                                    px,
                                    py,
                                    pageWidth,
                                    currentDoc,
                                    pagePosition
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
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnRotate.setOnClickListener {
            isPortrait = !isPortrait
            requestedOrientation = if (isPortrait)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        binding.btnShowToc.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            tocFragment.setShowingToc(true)
        }
        binding.btnShowBookmarks.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            tocFragment.setShowingToc(false)
        }

        binding.zoomIt.setOnClickListener {
            toggleBars(!barsVisible)
        }
        binding.stopLinks.setOnClickListener {
            toggleLinks(!linksWorking)
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
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                    rv.setScaleFactor(zoom, page - 1, 0.5f)
                    toggleBars(false)
                    updatePageText(page - 1, totalPages)
                }
            }
        }
    }

    private fun checkFollowLinks(
        rv: PinchRecyclerView,
        x: Float,
        y: Float,
        w: Float,
        document: Document?,
        pageNumber: Int
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
                    if (linksWorking) {
                        handleExternalLink(uri)
                    }
                } else {
                    page.destroy()
                    if (linksWorking) {
                        handleInternalLink(rv, link, w)
                    }
                }
                break
            }
        }
    }

    private fun checkIfTopOfPageClicked(
        document: Document?,
        rv: PinchRecyclerView,
        x: Float,
        y: Float,
        width: Float,
        currentPage: Int
    ): Boolean {
        if (!linksWorking) {
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
            rv.setScaleFactor(finalScale, currentPage, pageCoordinates.translatingPercentage)
            toggleBars(false)
            updatePageText(currentPage, totalPages)
        }
        return pageCoordinates.found
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val actionBarHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics
            ).toInt()

            binding.toolbar.layoutParams.height = actionBarHeight + systemBars.top
            binding.toolbar.setPadding(0, systemBars.top, 0, 0)

            val bottomInset = systemBars.bottom.coerceAtLeast(ime.bottom)

            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                bottomInset
            )

            binding.tocPanel.setPadding(0, 0, 0, bottomInset)
            binding.zoomIt.setPadding(0, 0, 0, bottomInset)

            insets
        }
    }

    private fun updatePageText(current: Int, total: Int) {
        binding.pageIndicator.text = getString(R.string.page_indicator, current + 1, total)
    }

    private fun toggleLinks(allow: Boolean) {
        linksWorking = allow
        if (linksWorking) {
            val color = ContextCompat.getColor(this, R.color.links_working)
            binding.stopLinks.imageTintList = ColorStateList.valueOf(color)
            binding.stopLinks.setImageResource(R.drawable.ic_link_on)
        } else {
            val colorOff = ContextCompat.getColor(this, R.color.links_off)
            binding.stopLinks.imageTintList = ColorStateList.valueOf(colorOff)
            binding.stopLinks.setImageResource(R.drawable.ic_link_off)
        }

    }

    private fun toggleBars(show: Boolean) {
        if (barsVisible == show) return
        barsVisible = show

        val translationTop = if (show) 0f else -binding.toolbar.height.toFloat()
        val translationBottom = if (show) 0f else binding.bottomBar.height.toFloat()
        val translationBottomFab =
            if (barsVisible) translationBottom else translationBottom - binding.zoomIt.height
        binding.toolbar.animate().translationY(translationTop).setDuration(300).start()
        binding.bottomBar.animate().translationY(translationBottom).setDuration(300).start()
        binding.btnRotate.animate().translationY(translationBottom).setDuration(300).start()
        binding.zoomIt.animate().translationY(translationBottomFab).setDuration(300).start()
        binding.stopLinks.animate().translationY(translationBottomFab).setDuration(300).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        document?.destroy()
    }
}