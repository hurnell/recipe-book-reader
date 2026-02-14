package apk.hurnell.recipebookreader

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import apk.hurnell.recipebookreader.adapters.SimpleAdapter
import apk.hurnell.recipebookreader.databinding.ActivityRecipeBookBinding
import apk.hurnell.recipebookreader.ui.PinchRecyclerView

class RecipeBookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeBookBinding
    private var isPortrait = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityRecipeBookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Sample items
        val items = List(50) { "Item #$it" }

        val adapter = SimpleAdapter(items)
        val recyclerView: PinchRecyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // FAB toggles orientation
        binding.fabToggleOrientation.setOnClickListener {
            isPortrait = !isPortrait
            requestedOrientation = if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}
