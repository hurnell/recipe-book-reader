package apk.hurnell.recipebookreader

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.RecentRecipesAdapter
import apk.hurnell.recipebookreader.databinding.ActivityRecentRecipesBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.CategoryItem
import apk.hurnell.recipebookreader.model.RecentRecipeItem
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

data class RecentRecipesTracker(
    @SerializedName("offset") val offset: Int?
) : BaseTracker()

class RecentRecipesActivity : BaseDrawerActivity() {

    private var _binding: ActivityRecentRecipesBinding? = null
    private val binding get() = _binding!!
    private lateinit var recentRecipesAdapter: RecentRecipesAdapter
    private val configurationKey = "RecentRecipesConfiguration"
    private var bookmarkData: List<RecentRecipeItem> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityRecentRecipesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadingOverlay = binding.loadingOverlay
        loadingOverlay.visibility = View.GONE

        binding.bookmarksRecyclerView.layoutManager = LinearLayoutManager(this)
        recipeImagePreviewWrapper = findViewById(R.id.recipeImagePreviewWrapper)
        recipeImagePreview = findViewById(R.id.recipeImagePreview)
        recipeImagePreviewTitle = findViewById(R.id.recipeImagePreviewTitle)
        recipeImageBookTitle = findViewById(R.id.recipeImageBookTitle)
        closePreviewButton = findViewById(R.id.closePreviewButton)
        closePreviewButton?.setOnClickListener { hideRecipeImagePreview() }

        binding.bookmarksRecyclerView.itemAnimator = null

        bookmarkData = reloadRecentRecipes(false)
        recentRecipesAdapter = RecentRecipesAdapter(onClick = { item ->
            if (item.bookLocation != null) {
                val file = File(item.bookLocation)
                processAndOpenBook(file, item)
            }
        }, onLongClick = { item ->
            displaySnackBarMessage(item.title, binding.rootLayout)
        }
        )
        binding.bookmarksRecyclerView.adapter = recentRecipesAdapter

        populateAdapter()
        setupDrawer(binding.bookmarksToolbar)
    }


    private fun reloadRecentRecipes(applyAfter: Boolean): List<RecentRecipeItem> {
        val recentRecipes = repository.getAllRecentRecipes()
        if (applyAfter) {
            recentRecipesAdapter.submitList(recentRecipes)
        }
        return recentRecipes
    }

    fun populateAdapter() {
        lifecycleScope.launch {
            val tracker = dataStoreManager.recentRecipeState.firstOrNull()
            if (tracker != null) {
                val layoutManager =
                    binding.bookmarksRecyclerView.layoutManager as? LinearLayoutManager

                reloadRecentRecipes(true)
            }
        }
        recentRecipesAdapter.submitList(bookmarkData)

    }

    override fun refreshFilesAndUI(reloadAdapter: Boolean) {

    }

    private fun saveRecentRecipesActivity() {
        val dataStoreManager = DataStoreManager(applicationContext)
        lifecycleScope.launch {
            dataStoreManager.saveLastActivity(this@RecentRecipesActivity::class.java.name)

            val currentTracker = RecentRecipesTracker(
                offset = 0
            )
            dataStoreManager.saveTracker(
                DataStoreManager.RECENT_RECIPES_KEY,
                currentTracker,
                saveCurrent = true,
                addToHistory = true
            )
        }
    }

    override fun onPause() {
        super.onPause()
        saveRecentRecipesActivity()
    }
}