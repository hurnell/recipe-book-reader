package apk.hurnell.recipebookreader.model


data class Book(
    val id: Long,
    val sha: String?,
    val name: String?,
    val location: String?,
    val author: String?,
    val lastOpened: Long?,
    val tocCreated: Long?,
    val tocUnavailable: Int?,
    val category: String?,
    val subCategory: String?,
    val alternateCover: String?
)
