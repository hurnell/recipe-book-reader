package apk.hurnell.recipebookreader

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apk.hurnell.recipebookreader.adapters.BookShelfAdapter
import apk.hurnell.recipebookreader.model.FileItem

class BookShelfActivity : BaseDrawerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_shelf)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setupDrawer(toolbar)

        val recyclerView = findViewById<RecyclerView>(R.id.shelfRecyclerView)
// Standard vertical list, because the ADAPTER handles the 3-wide grid now
        recyclerView.layoutManager = LinearLayoutManager(this)

        val bookRowAdapter = BookShelfAdapter()
        recyclerView.adapter = bookRowAdapter

        // --- Logic to add 10 empty rows ---
        val actualBooks: List<FileItem> = getMyBooks() // Fetch your real data

        // Create a mutable list starting with your books
        val displayList = actualBooks.toMutableList<FileItem?>()

        // Calculate how many slots we need to reach 10 full rows (30 slots)
        val totalSlotsNeeded = 30
        val emptySlotsToAdd = (totalSlotsNeeded - displayList.size).coerceAtLeast(0)

        repeat(emptySlotsToAdd) {
            displayList.add(null) // Add 'null' to represent an empty spot on the shelf
        }

        bookRowAdapter.setRows(displayList)    }

    private fun getMyBooks(): List<FileItem> {
        // Return your actual list from DB or folder
        return emptyList()
    }
}