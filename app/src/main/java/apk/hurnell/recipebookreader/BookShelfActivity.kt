package apk.hurnell.recipebookreader

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BookShelfActivity : BaseDrawerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_shelf)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)

        val recyclerView = findViewById<RecyclerView>(R.id.shelfRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
    }
}