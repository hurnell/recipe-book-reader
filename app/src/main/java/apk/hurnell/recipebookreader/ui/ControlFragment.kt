package apk.hurnell.recipebookreader.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import apk.hurnell.recipebookreader.BaseDrawerActivity
import apk.hurnell.recipebookreader.BookShelfActivity
import apk.hurnell.recipebookreader.BookmarksActivity
import apk.hurnell.recipebookreader.EveryTocActivity
import apk.hurnell.recipebookreader.FileBrowserActivity
import apk.hurnell.recipebookreader.RecentBooksActivity
import apk.hurnell.recipebookreader.databinding.FragmentControlBinding

class ControlFragment : Fragment() {
    private lateinit var binding: FragmentControlBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun getCurrentActivity(): String? {
        val currentActivity = activity ?: return null
        return currentActivity::class.simpleName
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRecentBooks.setOnClickListener {
            if (getCurrentActivity() != "RecentBooksActivity") {
                navigateTo(RecentBooksActivity::class.java)
            } else {
                closeDrawer()
            }
        }
        binding.btnBookShelf.setOnClickListener {
            if (getCurrentActivity() != "BookShelfActivity") {
                navigateTo(BookShelfActivity::class.java)
            } else {
                closeDrawer()
            }
        }

        binding.btnBrowseFiles.setOnClickListener {
            if (getCurrentActivity() != "FileBrowserActivity") {
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
                                android.R.anim.fade_in, android.R.anim.fade_out
                            )
                            current.finish()
                        }
                    })
                }
            } else {
                closeDrawer()
            }
        }

        binding.btnSearchEveryToc.setOnClickListener {
            if (getCurrentActivity() != "EveryTocActivity") {
                navigateTo(EveryTocActivity::class.java)
            } else {
                closeDrawer()
            }
        }
        binding.btnBookmarks.setOnClickListener {
            if (getCurrentActivity() != "BookmarksActivity") {
                navigateTo(BookmarksActivity::class.java)
            } else {
                closeDrawer()
            }
        }
        binding.btnCloseDrawer.setOnClickListener {
            val baseActivity = activity as? BaseDrawerActivity
            baseActivity?.drawerLayout?.closeDrawer(GravityCompat.START)
        }
    }
    private fun closeDrawer(){
        val currentActivity = activity ?: return
        val drawer = (currentActivity as? BaseDrawerActivity)?.drawerLayout
        drawer?.closeDrawers()
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
                    android.R.anim.fade_in, android.R.anim.fade_out
                )

                if (currentActivity !is FileBrowserActivity) {
                    currentActivity.finish()
                }
            }
        })
    }
}