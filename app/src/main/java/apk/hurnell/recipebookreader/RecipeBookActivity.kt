package apk.hurnell.recipebookreader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.BookAdapter
import apk.hurnell.recipebookreader.databinding.ActivityRecipeBookBinding
import apk.hurnell.recipebookreader.helpers.PdfStreamer
import apk.hurnell.recipebookreader.ui.TocFragment
import com.artifex.mupdf.fitz.Document
import java.io.File

class RecipeBookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeBookBinding
    private var isPortrait = true
    private var barsVisible = true
    private var document: Document? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityRecipeBookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestStoragePermission()
        setupWindowInsets()
        binding.drawerLayout.isFocusableInTouchMode = false
        val pdfFile = File("/storage/emulated/0/Documents/moon/moon/british/nigella_bites_a.pdf")
        var title = "Recipe Book"
        if (pdfFile.exists()) {
            val stream = PdfStreamer(contentResolver, pdfFile.toUri())
            document = Document.openDocument(stream, "application/pdf")
            title = document?.getMetaData(Document.META_INFO_TITLE) ?: title
        }

        val adapter = document?.let { BookAdapter(it) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        val divider = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)
        binding.recyclerView.adapter = adapter

        val totalPages = adapter?.itemCount ?: 0
        updatePageText(0, totalPages)

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.pageSeekBar.max = if (totalPages > 0) totalPages - 1 else 0
        binding.pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePageText(progress, totalPages)
                if (fromUser) binding.recyclerView.scrollToPosition(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val currentPosition = layoutManager.findFirstVisibleItemPosition()
                binding.pageSeekBar.progress = currentPosition

                if (barsVisible && Math.abs(dy) > 10) toggleBars(false)
            }
        })

        binding.recyclerView.setOnClickListener {
            toggleBars(!barsVisible)
        }

        binding.btnRotate.setOnClickListener {
            isPortrait = !isPortrait
            requestedOrientation = if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        binding.btnTOC.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.tocToolbar.setNavigationOnClickListener {
            val imm =
                getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }

            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        if (savedInstanceState == null && document != null) {
            val tocFragment = TocFragment.newInstance(document!!) { page ->
                binding.recyclerView.scrollToPosition(page)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.tocFragmentContainer, tocFragment)
                .commit()
        }
        val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val imm =
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    val isKeyboardVisible = ViewCompat.getRootWindowInsets(binding.root)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

                    if (isKeyboardVisible || currentFocus != null) {
                        imm.hideSoftInputFromWindow(binding.drawerLayout.windowToken, 0)

                        currentFocus?.clearFocus()

                        Log.i("NIGEL_HURNELL", "Back caught: Hiding keyboard, drawer remains open")
                        return
                    }

                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
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

            binding.tocFragmentContainer.setPadding(0, 0, 0, bottomInset)

            insets
        }
    }

    private fun updatePageText(current: Int, total: Int) {
        binding.pageIndicator.text = "${current + 1} / $total"
    }

    private fun toggleBars(show: Boolean) {
        if (barsVisible == show) return
        barsVisible = show
        val translationTop = if (show) 0f else -binding.toolbar.height.toFloat()
        val translationBottom = if (show) 0f else binding.bottomBar.height.toFloat()

        binding.toolbar.animate().translationY(translationTop).setDuration(300).start()
        binding.bottomBar.animate().translationY(translationBottom).setDuration(300).start()
        binding.btnRotate.animate().translationY(translationBottom).setDuration(300).start()
    }

    private fun requestStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        document?.destroy()
    }
}