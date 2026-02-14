package apk.hurnell.recipebookreader

import android.app.Application
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

class RecipeBookApplication : Application() {

    companion object {
        init {
            // Load your native library
            System.loadLibrary("native-lib")
        }

        var logErrors: Boolean = true
    }

    // Native method (same mangled name as old Java)
    external fun qzZOdiQCvJTdsCGKBUvuCfTqm(): String

    override fun onCreate() {
        super.onCreate()

        // Initialize Realm

        // Build Realm configuration
        val config = RealmConfiguration.Builder(
            schema = setOf() // Add your RealmObject classes here, e.g., Recipe::class
        )
            .name("recipe_book.realm") // new database name
            .encryptionKey(getRealmKey())
            .schemaVersion(1)
            .build()

        // Open Realm or store globally if needed
        Realm.open(config)

        // Set up global exception logging
    }

    private fun getRealmKey(): ByteArray {
        val stringKey = qzZOdiQCvJTdsCGKBUvuCfTqm()
        return try {
            val bytes = stringKey.toByteArray(Charsets.UTF_8)
            // Ensure 64-byte key
            if (bytes.size >= 64) bytes.copyOf(64) else bytes.copyOf(64)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(64)
        }
    }
}
