package apk.hurnell.recipebookreader

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

abstract class BaseDrawerActivity : AppCompatActivity() {

    lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    // Store the callback so we can enable/disable it dynamically
    private val drawerBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Add the callback to the dispatcher
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)
    }

    protected fun setupDrawer(toolbar: Toolbar) {
        drawerLayout = findViewById(R.id.drawer_layout)
        setSupportActionBar(toolbar)

        // Ensure the content doesn't hide behind the status bar
        window.statusBarColor = getColor(R.color.pastel_blue)

        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }
}