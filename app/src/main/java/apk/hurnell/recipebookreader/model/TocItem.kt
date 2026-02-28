package apk.hurnell.recipebookreader.model

data class TocItem(
    val tocId:Long,
    val bookId: Long? = null,
    val bookTitle: String? = null,
    val bookLocation: String? = null,
    val parentId: Int? = null,
    val title: String,
    val hierarchy: String? = null,
    val page: Int,
    val level: Int,
    val bookmarkId: Int? = null,
    val offset: Float = 0f,
    val scale: Float = 1f,
    val translate: Float = 0f,
    val children: List<TocItem> = emptyList(),
    var isExpanded: Boolean = false
){
    fun toBookmarkItem(): BookmarkItem{
        return BookmarkItem(
            tocId = tocId,
            bookmarkId = bookmarkId?.toLong(),
            title = title,
            bookTitle = bookTitle,
            bookId = bookId,
            page = page,
            offset = offset,
            scale = scale,
            translate = translate
        )
    }
}