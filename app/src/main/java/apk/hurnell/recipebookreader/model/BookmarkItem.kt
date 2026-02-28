package apk.hurnell.recipebookreader.model

data class BookmarkItem(
    val tocId: Long,
    val bookmarkId: Long?,
    val title: String,
    val bookTitle: String?,
    val bookId: Long?,
    val page: Int,
    val offset: Float = 0f,
    val scale: Float = 1f,
    val translate: Float = 0f
)