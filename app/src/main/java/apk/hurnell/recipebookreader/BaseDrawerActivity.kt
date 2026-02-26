package apk.hurnell.recipebookreader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.CoverPickerAdapter
import apk.hurnell.recipebookreader.helpers.GetCoverUrlHelper
import apk.hurnell.recipebookreader.helpers.PdfRepository
import apk.hurnell.recipebookreader.model.Book
import apk.hurnell.recipebookreader.ui.EditableCategoryView
import apk.hurnell.recipebookreader.ui.EditableTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import apk.hurnell.recipebookreader.model.TocItem
import com.bumptech.glide.Glide
import com.google.gson.Gson
import java.io.FileOutputStream

abstract class BaseDrawerActivity : AppCompatActivity() {

    lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    protected lateinit var repository: PdfRepository
    protected var categories = mutableListOf("All")
    protected var currentCategory = "All"
    protected lateinit var spinner: Spinner


    protected lateinit var loadingOverlay: LinearLayout

    protected var overlayContainer: FrameLayout? = null
    protected var bookInfoOverlay: ScrollView? = null
    protected var bookTitle: EditableTextView? = null
    protected var bookAuthor: EditableTextView? = null
    protected var bookSavePath: TextView? = null
    protected var bookCategory: EditableCategoryView? = null
    protected var bookSubCategory: EditableCategoryView? = null
    protected var bookPreviewImage: ImageView? = null
    protected var isbnNumber: EditableTextView? = null
    protected var btnSearchCovers: ImageButton? = null
    protected var btnCloseGallery: ImageButton? = null
    protected var coverOptionsRecycler: RecyclerView? = null

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
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)
    }

    abstract fun refreshFilesAndUI()

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        initBaseViews()
    }

    protected fun refreshCategories() {
        val usedCategories = repository.getUsedCategories()

        val set = LinkedHashSet<String>()
        set.add("All") // always first
        usedCategories.forEach { if (it.isNotBlank()) set.add(it) }

        categories = set.toMutableList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    protected fun processAndOpenBook(pdfFile: File, tocItem: TocItem? = null) {
        loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tocJson = tocItem?.let { Gson().toJson(it) }
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    val intent =
                        Intent(this@BaseDrawerActivity, RecipeBookActivity::class.java).apply {
                            putExtra("PDF_PATH", pdfFile.absolutePath)
                            if (tocJson != null) {
                                putExtra("TOC_ITEM_JSON", tocJson)
                            }
                        }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        this@BaseDrawerActivity,
                        "Error opening PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("NIGEL_HURNELL", "Failed to open PDF", e)
                }
            }
        }
    }

    private fun initBaseViews() {
        drawerLayout = findViewById(R.id.drawer_layout)

        overlayContainer = findViewById(R.id.overlayContainer)
        bookInfoOverlay = findViewById(R.id.bookInfoOverlay)
        bookPreviewImage = findViewById(R.id.bookPreviewImage)
        bookSavePath = findViewById(R.id.bookSavePath)
        isbnNumber = findViewById(R.id.isbnNumber)
        bookTitle = findViewById(R.id.bookTitle)
        bookAuthor = findViewById(R.id.bookAuthor)
        bookCategory = findViewById(R.id.bookCategory)
        bookSubCategory = findViewById(R.id.bookSubCategory)

        overlayContainer?.setOnClickListener { hideBookInfoOverlay() }

    }

    fun showPossibleBookCovers(book: Book) {

        btnSearchCovers?.visibility = View.GONE
        if(book.name != null && book.author != null) {
            lifecycleScope.launch {
                val urls = GetCoverUrlHelper().getAllAvailableCovers(book)
                for (url in urls){
                    Log.e("A", url)
                }
                if (urls.isNotEmpty()) {
                    bookPreviewImage?.visibility = View.GONE
                    coverOptionsRecycler?.visibility = View.VISIBLE
                    btnCloseGallery?.visibility = View.VISIBLE
                    btnSearchCovers?.visibility = View.GONE

                    // Set up RecyclerView
                    coverOptionsRecycler?.adapter = CoverPickerAdapter(urls) { selectedUrl ->
                        // User picked one!
                        // 1. Load it into the main preview
                        //Glide.with(this@BaseDrawerActivity).load(selectedUrl).into(bookPreviewImage!!)

                        // 2. Hide the gallery and show the main image again
                        coverOptionsRecycler?.visibility = View.GONE
                        bookPreviewImage?.visibility = View.VISIBLE
                        btnCloseGallery?.visibility = View.GONE
                        btnSearchCovers?.visibility = View.VISIBLE
                        if (book.sha != null) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val success = saveCoverAsPng(selectedUrl, book.sha)

                                if (success) {
                                    withContext(Dispatchers.Main) {
                                        setResetPreviewImage(book.sha, bookPreviewImage!!)
                                    }
                                }
                            }
                        }

                    }
                }
            }

        }
    }
    private fun saveCoverAsPng(url: String, sha: String): Boolean {
        return try {
            // 1. Fetch the bitmap from Glide synchronously (must be on IO thread)
            val bitmap = Glide.with(this)
                .asBitmap()
                .load(url)
                .submit()
                .get() // This waits for the download to finish

            // 2. Prepare the destination file
            val hashName = "${sha}.png"
            val thumbnailFile = File(this.filesDir, hashName)

            // 3. Save as PNG
            FileOutputStream(thumbnailFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.e("SAVE_COVER", "Error saving PNG for $sha", e)
            false
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



                withContext(Dispatchers.Main) {
                    bookTitle?.setParams(book.name ?: pdfFile.name, "Title", Typeface.BOLD)
                    bookTitle?.onAccept { newText ->
                        repository.updateBookStringParam(book.id, "name", newText)
                    }
                    bookAuthor?.setParams(book.author?.ifBlank { "" } ?: "", "Author")
                    bookAuthor?.onAccept { newText ->
                        repository.updateBookStringParam(book.id, "author", newText)
                    }
                    isbnNumber?.setParams(book.isbn?.ifBlank { "" } ?: "", "ISBN")
                    isbnNumber?.onAccept { newText ->
                        repository.updateBookStringParam(book.id, "isbn", newText)
                    }
                    bookCategory?.setParams(repository, book.category, "Category")
                    bookCategory?.onAccept { name, categoryId ->

                        if (categoryId != null) {
                            repository.updateBookCategory(book.id, "category", categoryId)
                        } else if (name.isNotBlank()) {
                            val newId = repository.createCategory(name)
                            repository.updateBookCategory(book.id, "category", newId)
                            bookCategory?.setNewCategoryId(newId.toInt())
                        }
                    }
                    bookSubCategory?.setParams(
                        repository,
                        book.subCategory,
                        "Sub Category"
                    )
                    bookSubCategory?.onAccept { name, categoryId ->

                        if (categoryId != null) {
                            repository.updateBookCategory(book.id, "sub_category", categoryId)
                        } else if (name.isNotBlank()) {
                            val newId = repository.createCategory(name)
                            repository.updateBookCategory(book.id, "sub_category", newId)
                            bookSubCategory?.setNewCategoryId(newId.toInt())
                        }
                    }
                    setResetPreviewImage(book.sha!!, previewImage)
                    bookSavePath?.text = book.location
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
                coverOptionsRecycler = findViewById(R.id.coverOptionsRecycler)
                btnSearchCovers = findViewById(R.id.btnSearchCovers)
                btnCloseGallery = findViewById(R.id.btnCloseGallery)
                btnSearchCovers?.setOnClickListener {
                    showPossibleBookCovers(book)
                }
                btnCloseGallery?.setOnClickListener {
                    coverOptionsRecycler?.visibility = View.GONE
                    btnCloseGallery?.visibility = View.GONE
                    bookPreviewImage?.visibility = View.VISIBLE
                    btnSearchCovers?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("NIGEL_HURNELL", "Error opening PDF: ${e.message}", e)
            }
        }
    }

    private fun setResetPreviewImage(sha: String, previewImage:ImageView){
        val thumbnailFile = File(
            previewImage.context.filesDir,
            "${sha}.png"
        )
        val bitmap = BitmapFactory.decodeFile(thumbnailFile.absolutePath)

        previewImage.setImageBitmap(bitmap)
        bookPreviewImage?.setImageBitmap(bitmap)
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
                btnCloseGallery?.visibility = View.GONE
                btnSearchCovers?.visibility = View.VISIBLE
                this.refreshFilesAndUI()
            }
            ?.start()
    }

    protected fun setupDrawer(toolbar: Toolbar) {
        drawerLayout = findViewById(R.id.drawer_layout)
        setSupportActionBar(toolbar)

        window.statusBarColor = getColor(R.color.pastel_blue)

        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }
}