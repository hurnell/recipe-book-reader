package apk.hurnell.recipebookreader

import android.app.AlertDialog
import com.artifex.mupdf.fitz.*
import java.nio.ByteBuffer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.EveryTocAdapter
import apk.hurnell.recipebookreader.model.TocItem
import androidx.coordinatorlayout.widget.CoordinatorLayout
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import apk.hurnell.recipebookreader.databinding.ActivityEveryTocBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.graphics.createBitmap
import com.artifex.mupdf.fitz.StructuredTextWalker

data class EveryTocTracker(
    val currentCategory: String,
    val searchTerm: String,
    val lastScrollPosition: Int,
    val lastScrollOffset: Int
) : BaseTracker()

class EveryTocActivity : BaseDrawerActivity() {
    private var _binding: ActivityEveryTocBinding? = null
    private val binding get() = _binding!!
    private var searchJob: Job? = null
    private lateinit var adapter: EveryTocAdapter
    private var lastScrollPosition = 0
    private var lastScrollOffset = 0
    private var currentSearchTerm: String = ""
    private var currentCount: String = ""
    private var recipeImagePreviewWrapper: FrameLayout? = null
    private var recipeImagePreviewTitle: TextView? = null
    private var recipeImageBookTitle: TextView? = null
    private var recipeImagePreview: ImageView? = null
    private var closePreviewButton: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityEveryTocBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadingOverlay = binding.loadingOverlay
        loadingOverlay.visibility = View.GONE
        setupDrawer(binding.everyTocToolbar)
        spinner = binding.categorySpinner
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentCategory = parent.getItemAtPosition(position) as String
                applyChosenTextAndCategory()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                currentCategory = "All"
                applyChosenTextAndCategory()
            }
        }

        binding.everyTocRecyclerView.layoutManager = LinearLayoutManager(this)
        recipeImagePreviewWrapper = findViewById(R.id.recipeImagePreviewWrapper)
        recipeImagePreview = findViewById(R.id.recipeImagePreview)
        recipeImagePreviewTitle = findViewById(R.id.recipeImagePreviewTitle)
        recipeImageBookTitle = findViewById(R.id.recipeImageBookTitle)
        closePreviewButton = findViewById(R.id.closePreviewButton)
        closePreviewButton?.setOnClickListener { hideRecipeImagePreview() }


        adapter = EveryTocAdapter(
            onLongClickTitle = { item ->
                displayClickResult(item.title, binding.rootLayout)
            },
            onClickTitle = { item ->
                if (item.bookLocation != null) {
                    val file = File(item.bookLocation)
                    processAndOpenBook(file, item)
                }
            },
            onClickBook = { item ->
                displayClickResult(item.bookTitle!!, binding.rootLayout)

            },
            onClickHierarchy = { item ->
                displayClickResult(item.hierarchy!!, binding.rootLayout)
            },
            onClickBookmark = { item ->
                if (item.bookmarkId == null) {
                    val success = repository.createBookmark(item.toBookmarkItem())
                    if (success) {
                        val toastText =
                            "✅Bookmark with title \"${item.title}\" for book \"${item.bookTitle}\" to bookmarks"
                        Toast.makeText(
                            this,
                            toastText,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    applyChosenTextAndCategory()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Bookmark?")
                        .setMessage("Are you sure you want to delete the bookmark?")
                        .setPositiveButton("Delete") { dialog, _ ->
                            val success = repository.deleteBookmark(item.toBookmarkItem())
                            if (success) {
                                val toastText = "❌ Bookmark with title \"${item.title}\" deleted"
                                Toast.makeText(
                                    this@EveryTocActivity,
                                    toastText,
                                    Toast.LENGTH_SHORT
                                ).show()

                            }
                            this@EveryTocActivity.applyChosenTextAndCategory()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            },
            onClickEye = { item ->
                if (item.bookLocation != null) {
                    val wrapper = recipeImagePreviewWrapper ?: return@EveryTocAdapter
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

            }
        )
        binding.everyTocRecyclerView.adapter = adapter
        binding.everyTocRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trackRecyclerViewOffset()
            }
        })
        binding.searchToc.setOnClickListener {
            it.hideKeyboard()
            applyChosenTextAndCategory()
        }
        binding.searchTocEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyChosenTextAndCategory()
                v.hideKeyboard()

                true
            } else {
                false
            }
        }
        addTextWatcher()
        applySavedSettings()

    }

    fun buildPageImageIntoView(item: TocItem) {
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
                Log.e("PDF_EXTRACT", "Extraction failed: ${e.message}")
                renderWholePageAsFallback(page)
            } finally {
                pixmap?.destroy()
                page?.destroy()
                document?.destroy()
            }
        }
    }

    private suspend fun renderWholePageAsFallback(page: Page?) {
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

    fun applySavedSettings() {
        lifecycleScope.launch {
            val tracker = dataStoreManager.everyTocState.firstOrNull()
            if (tracker != null) {
                currentCategory = tracker.currentCategory
                currentSearchTerm = tracker.searchTerm
                lastScrollPosition = tracker.lastScrollPosition
                lastScrollOffset = tracker.lastScrollOffset
                binding.searchTocEditText.text =
                    Editable.Factory.getInstance().newEditable(currentSearchTerm)

                val position = categories.indexOf(currentCategory)
                spinner.setSelection(position)
                applyChosenTextAndCategory(tracker)
            }
        }
        val position = categories.indexOf("All")
        spinner.setSelection(position)
        applyChosenTextAndCategory(null)

        refreshCategories()
    }

    private fun trackRecyclerViewOffset() {
        val layoutManager =
            binding.everyTocRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
        val offset = firstVisibleView?.top ?: 0

        lastScrollPosition = firstVisibleItemPosition
        lastScrollOffset = offset
    }

    fun applyChosenTextAndCategory(saved: EveryTocTracker? = null) {
        currentSearchTerm = binding.searchTocEditText.text.toString().trim()
        if (currentSearchTerm != "") {
            val everyToc: List<TocItem> =
                repository.getFilteredEveryToc(currentSearchTerm, currentCategory)
            currentCount = "${everyToc.size}"
            binding.resultCountTextView.text = currentCount
            adapter.submitList(everyToc) {
                if (saved != null) {
                    val layoutManager =
                        binding.everyTocRecyclerView.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(
                        saved.lastScrollPosition,
                        saved.lastScrollOffset
                    )
                }
            }
        } else {
            currentCount = ""
            binding.resultCountTextView.text = currentCount
            adapter.submitList(null)
        }
    }

    fun displayClickResult(text: String, rootLayout: CoordinatorLayout) {
        val snackBar = Snackbar.make(rootLayout, text, Snackbar.LENGTH_LONG)
        val textView =
            snackBar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 5
        snackBar.show()
    }

    fun addTextWatcher() {
        binding.searchTocEditText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating || s == null) return

                val original = s.toString()
                val filtered = original.lowercase().replace(Regex("[^a-z0-9 ]"), "")

                if (original != filtered) {
                    isUpdating = true
                    val selection = binding.searchTocEditText.selectionStart

                    s.replace(0, s.length, filtered)
                    binding.searchTocEditText.setSelection(selection.coerceAtMost(filtered.length))
                    isUpdating = false
                }
                adapter.submitList(null)
                var bv = View.VISIBLE

                currentSearchTerm = binding.searchTocEditText.text.toString().trim()
                if (binding.searchTocEditText.text.isNullOrEmpty()) {
                    bv = View.INVISIBLE
                    adapter.submitList(null)
                    binding.resultCountTextView.text = ""
                    currentSearchTerm = ""
                }
                binding.searchToc.visibility = bv
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)

                    if (currentSearchTerm.isNotEmpty()) {
                        val count = withContext(Dispatchers.IO) {
                            repository.searchBooks(
                                currentSearchTerm,
                                currentCategory
                            )
                        }
                        currentCount = "$count"
                    } else {
                        currentCount = ""
                    }
                    binding.resultCountTextView.text = currentCount

                }
            }
        })
    }

    fun View.hideKeyboard() {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun refreshFilesAndUI() {}
    fun hideRecipeImagePreview() {
        recipeImagePreviewWrapper?.visibility = View.GONE
    }

    private fun saveEveryTocTracker(
        category: String,
        searchTerm: String,
        position: Int,
        offset: Int
    ) {
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@EveryTocActivity::class.java.name)
            val currentTracker = EveryTocTracker(
                category,
                searchTerm,
                position,
                offset
            )
            dataStoreManager.saveTracker(
                DataStoreManager.EVERY_TOC_KEY,
                currentTracker,
                saveCurrent = true,
                addToHistory
            )
        }
    }

    override fun onPause() {
        super.onPause()
        saveEveryTocTracker(
            currentCategory,
            currentSearchTerm,
            lastScrollPosition,
            lastScrollOffset
        )
    }
}

