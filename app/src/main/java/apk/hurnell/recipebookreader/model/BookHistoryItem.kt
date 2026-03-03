package apk.hurnell.recipebookreader.model

data class BookHistoryItem(
    val id: Long? = null,
    val bookId: Long,
    val page: Int,
    val offset: Int?,
    val translationX: Float?,
    val scale: Float?
)