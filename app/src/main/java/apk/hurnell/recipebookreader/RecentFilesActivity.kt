package apk.hurnell.recipebookreader

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RecentFilesActivity : BaseDrawerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent_files)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)

        val recyclerView = findViewById<RecyclerView>(R.id.recentRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // adapter = RecentFilesAdapter(...)
    }
}