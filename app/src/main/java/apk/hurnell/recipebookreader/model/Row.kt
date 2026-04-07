package apk.hurnell.recipebookreader.model

data class Row(
    val id: Long,
    val bookId: Long,
    val bookTitle: String,
    val parentId: Long?,
    val title: String,
    val normalisedTitle: String,
    val bookmarkId: Int?,
    val page: Int,
    val level: Int,
    val scale: Float,
    val translate: Float,
    val parentTitle: String?
)