package apk.hurnell.recipebookreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.createBitmap
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.ui.EditableTextView
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

abstract class BaseDrawerActivity : AppCompatActivity() {

    lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    // overlay views
    protected var overlayContainer: FrameLayout? = null
    protected var bookInfoOverlay: ScrollView? = null
    protected var bookTitle: EditableTextView? = null
    protected var bookAuthor: EditableTextView? = null
    protected var bookCategory: EditableTextView? = null
    protected var bookSubCategory: EditableTextView? = null
    protected var bookPreviewImage: ImageView? = null
    // Store the callback so we can enable/disable it dynamically
    private val drawerBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }

    companion object {

        private const val LOG_TAG = "NIGEL_HURNELL"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Add the callback to the dispatcher
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)

    }
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        // now layout exists
        initBaseViews()
    }

    private fun initBaseViews() {
        drawerLayout = findViewById(R.id.drawer_layout)

        overlayContainer = findViewById(R.id.overlayContainer)
        bookInfoOverlay = findViewById(R.id.bookInfoOverlay)
        bookPreviewImage = findViewById(R.id.bookPreviewImage)
        bookTitle = findViewById(R.id.bookTitle)
        bookAuthor = findViewById(R.id.bookAuthor)
        bookCategory = findViewById(R.id.bookCategory)
        bookSubCategory = findViewById(R.id.bookSubCategory)

        overlayContainer?.setOnClickListener { hideBookInfoOverlay() }
    }

    protected fun showBookInfoOverlay(
        pdfFile: File
    ) {

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = PdfRepository(contentResolver, this@BaseDrawerActivity)
                val currentDocument = runCatching {
                    repository.openPdfFast(pdfFile)
                }.getOrElse {
                    Log.e(LOG_TAG, "Failed to open document", it)
                    finish()
                    return@launch
                }
                val book = repository.getOrCreateBook(
                    pdfFile,
                    pdfFile.absolutePath,
                    currentDocument
                ) ?: run {
                    Log.e(LOG_TAG, "Failed to create or fetch book")
                    finish()
                    return@launch
                }
                val firstPageBitmap: Bitmap? = try {
                    val page = currentDocument.loadPage(0)
                    val width = 600  // adjust for desired preview width

                    val pageWidth = page.bounds.x1 - page.bounds.x0
                    val pageHeight = page.bounds.y1 - page.bounds.y0
                    val scale = width / pageWidth
                    val height = (width.toFloat() / pageWidth * pageHeight).toInt()
                    val bitmap = createBitmap(width, height)
                    val device = AndroidDrawDevice(bitmap, 0, 0)
                    page.run(device, Matrix(scale, scale), null)
                    bitmap
                } catch (e: Exception) {
                    Log.e("NIGEL_HURNELL", "Failed to render first page: ${e.message}", e)
                    null
                }
                withContext(Dispatchers.Main) {
                    bookTitle?.setParams(book.name ?: pdfFile.name, "Title", Typeface.BOLD)
                    bookTitle?.onAccept { newText ->
                        repository.updateBookStringParam(book.id, "name", newText)
                    }
                    bookAuthor?.setParams(book.author?.ifBlank { "Unknown" } ?: "Unknown", "Author")
                    bookAuthor?.onAccept { newText ->
                        repository.updateBookStringParam(book.id, "author", newText)
                    }
                    bookCategory?.setParams(book.category ?: "Unknown", "Category")
                    bookCategory?.onAccept { newText ->
                        repository.updateBookStringParam(book.id, "category", newText)
                    }
                    bookSubCategory?.setParams(
                        book.subCategory ?: "Unknown",
                        "Sub Category",
                        true)
                    bookSubCategory?.onAccept { newText ->
                        repository.updateBookStringParam(book.id, "sub_category", newText)
                    }
                    bookPreviewImage?.setImageBitmap(firstPageBitmap)


                    overlayContainer?.visibility = View.VISIBLE
                    bookInfoOverlay?.scaleX = 0.8f
                    bookInfoOverlay?.scaleY = 0.8f
                    bookInfoOverlay?.alpha = 0f
                    bookInfoOverlay?.visibility = View.VISIBLE
                    bookInfoOverlay?.animate()
                        ?.alpha(1f)
                        ?.scaleX(1f)
                        ?.scaleY(1f)
                        ?.setDuration(250)
                        ?.start()
                }
            } catch (e: Exception) {
                Log.e("NIGEL_HURNELL", "Error opening PDF: ${e.message}", e)
            }
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
            }
            ?.start()
    }
    protected fun setupDrawer(toolbar: Toolbar) {
        drawerLayout = findViewById(R.id.drawer_layout)
        setSupportActionBar(toolbar)

        // Ensure the content doesn't hide behind the status bar
        window.statusBarColor = getColor(R.color.pastel_blue)

        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }
}