package apk.hurnell.recipebookreader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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

class RecipeBookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeBookBinding
    private var isPortrait = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityRecipeBookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestStoragePermission()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Sample items
        val items = List(50) { "Item #$it" }

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

        // FAB toggles orientation
        binding.fabToggleOrientation.setOnClickListener {
            isPortrait = !isPortrait
            requestedOrientation = if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun requestStoragePermission() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        }
    }
}
