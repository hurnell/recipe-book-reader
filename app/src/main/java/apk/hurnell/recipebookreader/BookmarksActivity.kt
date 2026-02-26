package apk.hurnell.recipebookreader

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import apk.hurnell.recipebookreader.helpers.PdfRepository

class BookmarksActivity : BaseDrawerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Link to the layout XML file
        setContentView(R.layout.activity_bookmarks)
        repository = PdfRepository(this)
        val toolbar: Toolbar = findViewById(R.id.bookmarksToolbar)

        setupDrawer(toolbar)

    }

    override fun refreshFilesAndUI() {

    }
}