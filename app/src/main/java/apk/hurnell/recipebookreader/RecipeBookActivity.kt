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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import apk.hurnell.recipebookreader.adapters.BookAdapter
import apk.hurnell.recipebookreader.adapters.SimpleAdapter
import apk.hurnell.recipebookreader.databinding.ActivityRecipeBookBinding
import apk.hurnell.recipebookreader.ui.PinchRecyclerView
import java.io.File
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.ui.TocFragment

class RecipeBookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeBookBinding
    private var isPortrait = true
    private var barsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityRecipeBookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestStoragePermission()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 1. Handle the Toolbar
            val toolbarParams = binding.toolbar.layoutParams
            // Standard ActionBar height is usually 56dp. We add the status bar height to it.
            val actionBarHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics
            ).toInt()

            toolbarParams.height = actionBarHeight + systemBars.top
            binding.toolbar.layoutParams = toolbarParams

            // Add padding to the top so the text/icons aren't inside the status bar
            binding.toolbar.setPadding(0, systemBars.top, 0, 0)

            // 2. Handle the Bottom Bar
            // We add padding to the bottom so it sits above the gesture navigation bar
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                systemBars.bottom
            )

            insets
        }

        val pdfFile = File("/storage/emulated/0/Documents/moon/moon/british/nigella_bites_a.pdf")
        val pdfUri: Uri = Uri.fromFile(pdfFile)

        val bookAdapter = BookAdapter(
            contentResolver = this.contentResolver,
            pdfUri = pdfUri,
            cacheDir = cacheDir
        )
        val recyclerView: PinchRecyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = bookAdapter
        val totalPages = bookAdapter.itemCount
        updatePageText(0, totalPages)
        binding.pageSeekBar.max = if (totalPages > 0) totalPages - 1 else 0
        binding.pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.recyclerView.scrollToPosition(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val currentPosition = layoutManager.findFirstVisibleItemPosition()
                binding.pageSeekBar.progress = currentPosition

                // Auto-hide bars when scrolling
                if (barsVisible && Math.abs(dy) > 10) {
                    toggleBars(false)
                }
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
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        binding.tocToolbar.setNavigationOnClickListener {
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        }
        binding.toolbar.title = "Recipe Book"
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
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tocFragmentContainer, TocFragment())
                .commit()
        }
    }
    private fun updatePageText(current: Int, total: Int) {
        binding.pageIndicator.text = "${current + 1} / $total"
    }
    private fun toggleBars(show: Boolean) {
        barsVisible = show
        val translationTop = if (show) 0f else -binding.toolbar.height.toFloat()
        val translationBottom = if (show) 0f else binding.bottomBar.height.toFloat()

        binding.toolbar.animate().translationY(translationTop).setDuration(300).start()
        binding.bottomBar.animate().translationY(translationBottom).setDuration(300).start()

        // Also move FAB so it doesn't get covered
        binding.btnRotate.animate().translationY(translationBottom).setDuration(300).start()
    }
    private fun requestStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        }
    }
}
