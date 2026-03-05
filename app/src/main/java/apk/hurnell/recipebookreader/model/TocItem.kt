package apk.hurnell.recipebookreader.model

data class TocItem(
    override val tocId:Long,
    override val bookId: Long? = null,
    override val bookTitle: String? = null,
    override val bookLocation: String? = null,
    val parentId: Int? = null,
    val title: String,
    val hierarchy: String? = null,
    override val page: Int,
    val level: Int,
    val bookmarkId: Int? = null,
    override val scale: Float = 1f,
    override val translate: Float = 0f,
    val children: List<TocItem> = emptyList(),
    var isExpanded: Boolean = false
    ): BaseBookmarkTocItem{
    fun toBookmarkItem(): BookmarkItem{
        return BookmarkItem(
            tocId = tocId,
            bookmarkId = bookmarkId?.toLong(),
            title = title,
            bookTitle = bookTitle,
            bookLocation = bookLocation,
            bookId = bookId,
            page = page,
            offset = null,
            scale = scale,
            translate = translate
        )
    }
}