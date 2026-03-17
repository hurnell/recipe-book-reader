package apk.hurnell.recipebookreader.helpers

import android.content.Context
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
import apk.hurnell.recipebookreader.RecipeBookTracker
import apk.hurnell.recipebookreader.model.BaseTracker
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.reflect.TypeToken

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
        val RECIPE_BOOK_KEY = stringPreferencesKey("recipe_book_tracker")
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
            if (addToHistory) {
                val currentHistoryJson = settings[TRACKER_HISTORY_KEY]
                val type = object : TypeToken<MutableList<String>>() {}.type
                val history: MutableList<String> = if (currentHistoryJson != null) {
                    gson.fromJson(currentHistoryJson, type)
                } else {
                    mutableListOf()
                }
                removeOldEntries(history, key.name, snapshot)

                val newEntry = HistoryEntry(keyName = key.name, trackerJson = snapshot)
                history.add(gson.toJson(newEntry))

                if (history.size > 100) history.removeAt(0)

                settings[TRACKER_HISTORY_KEY] = gson.toJson(history)
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
                val matchedJson = history.removeAt(indexToRemove)
                val entry = gson.fromJson(matchedJson, HistoryEntry::class.java)
                foundTracker = gson.fromJson(entry.trackerJson, FileBrowserTracker::class.java)
                settings[TRACKER_HISTORY_KEY] = gson.toJson(history)
            }
        }
        return foundTracker
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
                history.removeAt(history.size - 1)
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

    val bookmarksState = getTracker(BOOKMARKS_KEY, BookmarksTracker::class.java)
    val bookShelfState = getTracker(BOOK_SHELF_KEY, BookShelfTracker::class.java)
    val everyTocState = getTracker(EVERY_TOC_KEY, EveryTocTracker::class.java)
    val pdfFileBrowserState = getTracker(PDF_FILE_BROWSER_KEY, FileBrowserTracker::class.java)
    val imageFileBrowserState = getTracker(IMAGE_FILE_BROWSER_KEY, FileBrowserTracker::class.java)
    val recentBooksState = getTracker(RECENT_BOOKS_KEY, RecentBooksTracker::class.java)
    val recipeBookState = getTracker(RECIPE_BOOK_KEY, RecipeBookTracker::class.java)

}
