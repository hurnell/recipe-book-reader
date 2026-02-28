package apk.hurnell.recipebookreader.ui

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import apk.hurnell.recipebookreader.RecipeBookReaderApplication

class AppLifecycleListener(private val app: RecipeBookReaderApplication) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        println("App is in the FOREGROUND")
    }

    override fun onStop(owner: LifecycleOwner) {
        val lastActivity = app.lastActiveActivityName

        println("App went to background. The user was looking at: $lastActivity")

    }
}