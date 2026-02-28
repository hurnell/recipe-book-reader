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
import apk.hurnell.recipebookreader.BookmarksActivity
import apk.hurnell.recipebookreader.EveryTocActivity
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
        val btnSearchEveryToc = view.findViewById<Button>(R.id.btnSearchEveryToc)
        val btnBookmarks = view.findViewById<Button>(R.id.btnBookmarks)

        btnRecentBooks.setOnClickListener {
            navigateTo(RecentFilesActivity::class.java)
        }

        btnBookShelf.setOnClickListener {
            navigateTo(BookShelfActivity::class.java)
        }

        btnBrowseFiles.setOnClickListener {
            val current = activity ?: return@setOnClickListener
            val drawer = (current as? BaseDrawerActivity)?.drawerLayout

            if (current is FileBrowserActivity) {
                current.resetToRoot()
            } else {
                drawer?.closeDrawer(GravityCompat.START)

                drawer?.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerClosed(drawerView: View) {
                        drawer.removeDrawerListener(this)

                        val intent = Intent(requireContext(), FileBrowserActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                        current.overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        )
                        current.finish()
                    }
                })
            }
        }

        btnSearchEveryToc.setOnClickListener {
            navigateTo(EveryTocActivity::class.java)
        }
        btnBookmarks.setOnClickListener {
            navigateTo(BookmarksActivity::class.java)

        }
    }

    private fun navigateTo(destination: Class<*>) {
        val currentActivity = activity ?: return
        val drawer = (currentActivity as? BaseDrawerActivity)?.drawerLayout

        if (currentActivity.javaClass == destination) {
            drawer?.closeDrawers()
            return
        }

        drawer?.closeDrawer(GravityCompat.START)
        drawer?.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                drawer.removeDrawerListener(this)

                val intent = Intent(requireContext(), destination)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

                startActivity(intent)

                currentActivity.overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )

                if (currentActivity !is FileBrowserActivity) {
                    currentActivity.finish()
                }
            }
        })
    }
}