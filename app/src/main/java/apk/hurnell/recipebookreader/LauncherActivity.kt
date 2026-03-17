package apk.hurnell.recipebookreader


import android.os.Bundle

class LauncherActivity : BaseDrawerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        navigateBackToSavedActivity(null)
        finish()
    }

    override fun refreshFilesAndUI() {

    }
}