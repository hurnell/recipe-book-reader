package apk.hurnell.recipebookreader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        // 1. Initialize MuPDF Document
        val pdfFile = File("/storage/emulated/0/Documents/moon/moon/british/nigella_bites_a.pdf")
        if (pdfFile.exists()) {
            val stream = PdfStreamer(contentResolver, pdfFile.toUri())
            document = Document.openDocument(stream, "application/pdf")
        }

        // 2. Setup RecyclerView & Adapter
        val adapter = document?.let { BookAdapter(it) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 3. Setup UI State
        val totalPages = adapter?.itemCount ?: 0
        updatePageText(0, totalPages)

        // 4. Toolbar Setup
        binding.toolbar.title = "Recipe Book"
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 5. SeekBar Logic
        binding.pageSeekBar.max = if (totalPages > 0) totalPages - 1 else 0
        binding.pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePageText(progress, totalPages)
                if (fromUser) binding.recyclerView.scrollToPosition(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 6. Scroll & Tap Listeners
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

        // 7. Buttons (Rotate & TOC)
        binding.btnRotate.setOnClickListener {
            isPortrait = !isPortrait
            requestedOrientation = if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        binding.btnTOC.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.tocToolbar.setNavigationOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // 8. Load TOC Fragment (Passing the document)
        if (savedInstanceState == null && document != null) {
            val tocFragment = TocFragment.newInstance(document!!) { page ->
                binding.recyclerView.scrollToPosition(page)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.tocFragmentContainer, tocFragment)
                .commit()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val actionBarHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics
            ).toInt()

            binding.toolbar.layoutParams.height = actionBarHeight + systemBars.top
            binding.toolbar.setPadding(0, systemBars.top, 0, 0)

            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                systemBars.bottom
            )
            binding.tocFragmentContainer.setPadding(0, 0, 0, systemBars.bottom)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:$packageName".toUri()
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        document?.destroy()
    }
}