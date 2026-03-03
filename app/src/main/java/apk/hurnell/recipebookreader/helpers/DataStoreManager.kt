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
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class DataStoreManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        val LAST_ACTIVITY_KEY = stringPreferencesKey("last_activity")
        val BOOKMARKS_KEY = stringPreferencesKey("bookmarks_tracker")
        val BOOK_SHELF_KEY = stringPreferencesKey("bookshelf_tracker")
        val EVERY_TOC_KEY = stringPreferencesKey("every_toc_tracker")
        val FILE_BROWSER_KEY = stringPreferencesKey("file_browser_tracker")
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

    suspend fun <T> saveTracker(key: Preferences.Key<String>, tracker: T) {
        context.dataStore.edit { settings ->
            settings[key] = gson.toJson(tracker)
        }
    }

    fun <T> getTracker(key: Preferences.Key<String>, clazz: Class<T>): Flow<T?> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[key]
            if (json != null) gson.fromJson(json, clazz) else null
        }
    }
    val bookmarksState = getTracker(BOOKMARKS_KEY, BookmarksTracker::class.java)
    val bookShelfState = getTracker(BOOK_SHELF_KEY, BookShelfTracker::class.java)
    val everyTocState = getTracker(EVERY_TOC_KEY, EveryTocTracker::class.java)
    val fileBrowserState = getTracker(FILE_BROWSER_KEY, FileBrowserTracker::class.java)
    val recentBooksState = getTracker(RECENT_BOOKS_KEY, RecentBooksTracker::class.java)
    val recipeBookState = getTracker(RECIPE_BOOK_KEY, RecipeBookTracker::class.java)

}
