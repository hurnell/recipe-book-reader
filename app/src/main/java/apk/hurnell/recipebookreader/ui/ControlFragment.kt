package apk.hurnell.recipebookreader.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import apk.hurnell.recipebookreader.BaseDrawerActivity
import apk.hurnell.recipebookreader.BookShelfActivity
import apk.hurnell.recipebookreader.FileBrowserActivity
import apk.hurnell.recipebookreader.R
import apk.hurnell.recipebookreader.RecentFilesActivity

class ControlFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnRecentBooks = view.findViewById<Button>(R.id.btnRecentBooks)
        val btnBookShelf = view.findViewById<Button>(R.id.btnBookShelf)
        val btnBrowseFiles = view.findViewById<Button>(R.id.btnBrowseFiles)

        // Navigation for Recent Books
        btnRecentBooks.setOnClickListener {
            navigateTo(RecentFilesActivity::class.java)
        }

        // Navigation for Bookshelf
        btnBookShelf.setOnClickListener {
            navigateTo(BookShelfActivity::class.java)
        }

        // Navigation for File Browser
        btnBrowseFiles.setOnClickListener {
            val current = activity ?: return@setOnClickListener
            val drawer = (current as? BaseDrawerActivity)?.drawerLayout

            if (current is FileBrowserActivity) {
                current.resetToRoot()
            } else {
                // Close drawer first
                drawer?.closeDrawer(GravityCompat.START)

                drawer?.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerClosed(drawerView: View) {
                        drawer.removeDrawerListener(this)

                        val intent = Intent(requireContext(), FileBrowserActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                        current.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        current.finish()
                    }
                })
            }
        }
    }

    /**
     * Helper method to handle activity switching and drawer closing
     */
    private fun navigateTo(destination: Class<*>) {
        val currentActivity = activity ?: return
        val drawer = (currentActivity as? BaseDrawerActivity)?.drawerLayout

        if (currentActivity.javaClass == destination) {
            drawer?.closeDrawers()
            return
        }

        // 1. Start the drawer closing animation
        drawer?.closeDrawer(GravityCompat.START)

        // 2. Wait for the drawer to be fully closed before switching activities
        // This prevents the "jumping" and animation glitches
        drawer?.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                // Remove the listener so it doesn't fire again later
                drawer.removeDrawerListener(this)

                val intent = Intent(requireContext(), destination)
                // Use these flags to ensure a clean slide-in animation
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

                startActivity(intent)

                // Apply a consistent transition for all activities
                currentActivity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

                // Finish the current activity so the stack stays clean
                if (currentActivity !is FileBrowserActivity) {
                    currentActivity.finish()
                }
            }
        })
    }
}