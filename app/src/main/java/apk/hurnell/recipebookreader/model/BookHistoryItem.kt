package apk.hurnell.recipebookreader.model

data class BookHistoryItem(
    var id: Long? = null,
    var bookId: Long? = null,
    var page: Int,
    var offset: Int? = null,
    var translationX: Float?,
    var scale: Float?
)