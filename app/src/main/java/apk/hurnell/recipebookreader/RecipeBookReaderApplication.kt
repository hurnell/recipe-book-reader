package apk.hurnell.recipebookreader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ProcessLifecycleOwner
import apk.hurnell.recipebookreader.ui.AppLifecycleListener

class RecipeBookReaderApplication : Application() {
    var lastActiveActivityName: String? = null
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                lastActiveActivityName = activity.javaClass.simpleName
            }

            override fun onActivityCreated(activity: Activity, p1: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleListener(this))
    }
}