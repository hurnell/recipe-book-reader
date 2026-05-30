package apk.hurnell.recipebookreader.helpers

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import apk.hurnell.recipebookreader.BookShelfTracker
import apk.hurnell.recipebookreader.BookmarksTracker
import apk.hurnell.recipebookreader.EveryTocTracker
import apk.hurnell.recipebookreader.FileBrowserTracker
import apk.hurnell.recipebookreader.RecentBooksTracker
import apk.hurnell.recipebookreader.RecentRecipesTracker
import apk.hurnell.recipebookreader.RecipeBookTracker
import apk.hurnell.recipebookreader.model.BaseTracker
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

data class HistoryEntry(
    val keyName: String,
    val trackerJson: String
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class DataStoreManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        val LAST_ACTIVITY_KEY = stringPreferencesKey("last_activity")
        val TRACKER_HISTORY_KEY = stringPreferencesKey("tracker_history")
        val BOOKMARKS_KEY = stringPreferencesKey("bookmarks_tracker")
        val BOOK_SHELF_KEY = stringPreferencesKey("bookshelf_tracker")
        val EVERY_TOC_KEY = stringPreferencesKey("every_toc_tracker")
        val PDF_FILE_BROWSER_KEY = stringPreferencesKey("pdf_file_browser_tracker")
        val IMAGE_FILE_BROWSER_KEY = stringPreferencesKey("image_file_browser_tracker")
        val RECENT_BOOKS_KEY = stringPreferencesKey("recent_books_tracker")
        val RECENT_RECIPES_KEY = stringPreferencesKey("recent_recipes_tracker")
        val RECIPE_BOOK_KEY = stringPreferencesKey("recipe_book_tracker")
        private const val LOG_TAG = "NIGEL_HURNELL"
    }

    suspend fun saveLastActivity(activityName: String) {
        context.dataStore.edit { settings ->
            settings[LAST_ACTIVITY_KEY] = activityName
        }
    }

    val lastActivityFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_ACTIVITY_KEY]
        }

    suspend fun <T : BaseTracker> saveTracker(
        key: Preferences.Key<String>,
        tracker: T,
        saveCurrent: Boolean = true,
        addToHistory: Boolean = true
    ) {
        context.dataStore.edit { settings ->
            val snapshot = gson.toJson(tracker)
            if (saveCurrent) {
                settings[key] = snapshot
            }
            val currentHistoryJson = settings[TRACKER_HISTORY_KEY]
            val type = object : TypeToken<MutableList<String>>() {}.type
            val history: MutableList<String> = if (currentHistoryJson != null) {
                gson.fromJson(currentHistoryJson, type)
            } else {
                mutableListOf()
            }
            if (addToHistory) {
                removeOldEntries(history, key.name, snapshot)
                val newEntry = HistoryEntry(keyName = key.name, trackerJson = snapshot)
                history.add(gson.toJson(newEntry))

                if (history.size > 100) history.removeAt(0)
                settings[TRACKER_HISTORY_KEY] = gson.toJson(history)
            } else if(key.name == "pdf_file_browser_tracker" || key.name == "image_file_browser_tracker"){
                removeOldEntries(history, key.name, snapshot)
            }
        }
    }


    private fun removeOldEntries(
        history: MutableList<String>,
        keyName: String,
        newSnapshot: String
    ) {
        val mapType = object : TypeToken<Map<String, Any>>() {}.type

        val isFileBrowser =
            keyName == PDF_FILE_BROWSER_KEY.name || keyName == IMAGE_FILE_BROWSER_KEY.name

        if (isFileBrowser) {
            val newTrackerMap: Map<String, Any> = gson.fromJson(newSnapshot, mapType)
            val newDir = newTrackerMap["directory"]
            history.removeAll { existingJson ->
                try {
                    val existingEntry = gson.fromJson(existingJson, HistoryEntry::class.java)
                    if (existingEntry.keyName == keyName) {
                        val existingData: Map<String, Any> =
                            gson.fromJson(existingEntry.trackerJson, mapType)
                        existingData["directory"] == newDir
                    } else false
                } catch (e: Exception) {
                    false
                }
            }
        } else {
            history.removeAll { existingJson ->
                try {
                    val existingEntry = gson.fromJson(existingJson, HistoryEntry::class.java)
                    existingEntry.keyName == keyName
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    suspend fun getTotalEntries(currentDataStoreKey: String?, countAll: Boolean = false): Int {
        val preferences = context.dataStore.data.first()
        val historyJson = preferences[TRACKER_HISTORY_KEY] ?: return 0
        val type = object : TypeToken<List<String>>() {}.type
        val history: List<String> = gson.fromJson(historyJson, type)
        return history.count { json ->
            try {
                val entry = gson.fromJson(json, HistoryEntry::class.java)
                if (countAll) {
                    entry.keyName == currentDataStoreKey
                } else {
                    entry.keyName != currentDataStoreKey
                }
            } catch (e: Exception) { false }
        }
    }

    suspend fun logFullHistorySafely() {
        val preferences = context.dataStore.data.first()
        val historyJson = preferences[TRACKER_HISTORY_KEY] ?: return
        val type = object : TypeToken<List<String>>() {}.type
        val entries: List<String> = gson.fromJson(historyJson, type)
        Log.i(LOG_TAG, "--- Full History (${entries.size} items) ---")
        entries.forEachIndexed { index, json ->
            try {
                val entry = gson.fromJson(json, HistoryEntry::class.java)
                Log.i(
                    LOG_TAG,
                    "[$index] Key: ${entry.keyName} | Data: ${entry.trackerJson}"
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "[$index] Corrupt entry")
            }
        }
        logAll()
    }

    fun logOne(tracker: BaseTracker?, trackerName: String){
        if (tracker != null) {
            Log.i(LOG_TAG, "$trackerName: ${tracker.asJson()}")
        } else {
            Log.i(LOG_TAG, "$trackerName is null")
        }
    }

    suspend fun logAll(){
        val bookmarksTracker = bookmarksState.firstOrNull()
        logOne(bookmarksTracker, "bookmarksTracker")
        val everyTocTracker = everyTocState.firstOrNull()
        logOne(everyTocTracker, "everyTocTracker")
        val bookShelfTracker  = bookShelfState.firstOrNull()
        logOne(bookShelfTracker, "bookShelfTracker")
        val pdfFileBrowserTracker = pdfFileBrowserState.firstOrNull()
        logOne(pdfFileBrowserTracker, "pdfFileBrowserTracker")
        val imageFileBrowserTracker = imageFileBrowserState.firstOrNull()
        logOne(imageFileBrowserTracker, "imageFileBrowserTracker")
        val recentBooksTracker = recentBooksState.firstOrNull()
        logOne(recentBooksTracker, "recentBooksTracker")
        val recipeBookTracker = recipeBookState.firstOrNull()
        logOne(recipeBookTracker, "recipeBookTracker")
    }

    suspend fun findTrackerInHistoryByDirectory(
        targetPath: String,
        addToHistory: Boolean
    ): FileBrowserTracker? {
        var foundTracker: FileBrowserTracker? = null

        context.dataStore.edit { settings ->
            val historyJson = settings[TRACKER_HISTORY_KEY] ?: return@edit
            val type = object : TypeToken<MutableList<String>>() {}.type
            val history: MutableList<String> = gson.fromJson(historyJson, type)

            val indexToRemove = history.indexOfLast { json ->
                try {
                    if (json.contains(targetPath)) {
                        val entry = gson.fromJson(json, HistoryEntry::class.java)
                        val tracker =
                            gson.fromJson(entry.trackerJson, FileBrowserTracker::class.java)
                        tracker.directory == targetPath
                    } else false
                } catch (e: Exception) {
                    false
                }
            }

            if (indexToRemove != -1) {
                val matchedJson = history[indexToRemove]
                val entry = gson.fromJson(matchedJson, HistoryEntry::class.java)
                foundTracker = gson.fromJson(entry.trackerJson, FileBrowserTracker::class.java)
                settings[TRACKER_HISTORY_KEY] = gson.toJson(history)
            }
        }
        return foundTracker
    }

    suspend fun getLastHistoryEntry(): HistoryEntry? {
        val preferences = context.dataStore.data.firstOrNull() ?: return null
        val historyJson = preferences[TRACKER_HISTORY_KEY] ?: return null

        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val history: List<String> = gson.fromJson(historyJson, type)

            if (history.isNotEmpty()) {
                gson.fromJson(history.last(), HistoryEntry::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    suspend fun popAndGetPrevious(currentKeyName: String): HistoryEntry? {
        var previousEntry: HistoryEntry? = null

        context.dataStore.edit { settings ->
            val currentHistoryJson = settings[TRACKER_HISTORY_KEY] ?: return@edit
            val type = object : TypeToken<MutableList<String>>() {}.type
            val history: MutableList<String> = gson.fromJson(currentHistoryJson, type)

            if (history.isEmpty()) return@edit

            removeLast(history, currentKeyName)
            if (history.isNotEmpty()) {
                val prevJson = history.last()
                previousEntry = try {
                    gson.fromJson(prevJson, HistoryEntry::class.java)
                } catch (e: Exception) {
                    null
                }
            }
            settings[TRACKER_HISTORY_KEY] = gson.toJson(history)
        }
        return previousEntry
    }

    fun removeLast(history: MutableList<String>, currentKeyName: String) {
        val lastIndex = history.indexOfLast { json ->
            try {
                val entry = gson.fromJson(json, HistoryEntry::class.java)
                entry.keyName == currentKeyName
            } catch (e: Exception) {
                false
            }
        }
        if (lastIndex != -1) {
            history.removeAt(lastIndex)
        }
    }

    fun <T : BaseTracker> getTracker(key: Preferences.Key<String>, clazz: Class<T>): Flow<T?> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[key]
            if (json != null) gson.fromJson(json, clazz) else null
        }
    }

    suspend fun deleteCurrentFileDirectory(
        pdfOnly: Boolean,
        deleteTracker: FileBrowserTracker,
        deleteChildren: Boolean = false
    ) {
        val targetKeyName = if (pdfOnly) PDF_FILE_BROWSER_KEY.name else IMAGE_FILE_BROWSER_KEY.name
        val targetDirectory = deleteTracker.directory

        context.dataStore.edit { settings ->
            val currentHistoryJson = settings[TRACKER_HISTORY_KEY] ?: return@edit
            val type = object : TypeToken<MutableList<String>>() {}.type
            val history: MutableList<String> = gson.fromJson(currentHistoryJson, type)
            val removed = history.removeAll { json ->
                try {
                    val entry = gson.fromJson(json, HistoryEntry::class.java)
                    if (entry.keyName == targetKeyName) {
                        val tracker = gson.fromJson(entry.trackerJson, FileBrowserTracker::class.java)
                        if (deleteChildren) {
                            tracker.directory.startsWith(targetDirectory)
                        } else {
                            tracker.directory == targetDirectory
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (removed) {
                settings[TRACKER_HISTORY_KEY] = gson.toJson(history)
            }
        }
    }

    val bookmarksState = getTracker(BOOKMARKS_KEY, BookmarksTracker::class.java)
    val bookShelfState = getTracker(BOOK_SHELF_KEY, BookShelfTracker::class.java)
    val everyTocState = getTracker(EVERY_TOC_KEY, EveryTocTracker::class.java)
    val pdfFileBrowserState = getTracker(PDF_FILE_BROWSER_KEY, FileBrowserTracker::class.java)
    val imageFileBrowserState = getTracker(IMAGE_FILE_BROWSER_KEY, FileBrowserTracker::class.java)
    val recentBooksState = getTracker(RECENT_BOOKS_KEY, RecentBooksTracker::class.java)
    val recipeBookState = getTracker(RECIPE_BOOK_KEY, RecipeBookTracker::class.java)
    val recentRecipeState = getTracker(RECENT_RECIPES_KEY, RecentRecipesTracker::class.java)

}
