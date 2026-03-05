package apk.hurnell.recipebookreader.model

data class BookmarkItem(
    override val tocId: Long? = null,
    val bookmarkId: Long?,
    val title: String,
    override val bookTitle: String?,
    override val bookLocation: String?,
    override val bookId: Long?,
    override val page: Int,
    val offset: Int? = null,
    override val scale: Float = 1f,
    override val translate: Float = 0f
): BaseBookmarkTocItem