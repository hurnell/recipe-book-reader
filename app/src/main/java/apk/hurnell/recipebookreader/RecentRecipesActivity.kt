package apk.hurnell.recipebookreader

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import apk.hurnell.recipebookreader.adapters.RecentRecipesAdapter
import apk.hurnell.recipebookreader.databinding.ActivityRecentRecipesBinding
import apk.hurnell.recipebookreader.helpers.DataStoreManager
import apk.hurnell.recipebookreader.model.BaseTracker
import apk.hurnell.recipebookreader.model.RecentRecipeItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        }, onDeleteClick = { item ->
            val dialog = MaterialAlertDialogBuilder(
                this,
                R.style.ThemeOverlay_App_MaterialAlertDialog
            )
                .setTitle("Delete recent recipe item?")
                .setMessage("Are you sure you want to delete the recent recipe item with title \"${item.title}\"?")
                .setPositiveButton("Delete") { dialog, _ ->
                    val success = repository.deleteRecentRecipe(item)
                    if (success) {
                        val message = "❌ Recent recipe with title \"${item.title}\" deleted"
                        displaySnackBarMessage(message, binding.rootLayout)
                    }
                    this@RecentRecipesActivity.reloadRecentRecipes(true)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(), // 90% of screen width
                ViewGroup.LayoutParams.WRAP_CONTENT // height wraps content
            )
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            )
            dialog.window?.setBackgroundDrawableResource(R.drawable.alert_background)
            dialog.show()
        }, onLongClick = { item ->
            displaySnackBarMessage(item.title, binding.rootLayout)
        })
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

    override fun onResume() {
        super.onResume()
        reloadRecentRecipes(true)
    }

    override fun onPause() {
        super.onPause()
        saveRecentRecipesActivity()
    }
}