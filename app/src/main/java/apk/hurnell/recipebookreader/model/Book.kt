package apk.hurnell.recipebookreader.model


data class Book(
    val id: Long,
    val sha: String?,
    val name: String?,
    val location: String?,
    val author: String?,
    val isbn: String?,
    val scanned: Boolean,
    val lastOpened: Long?,
    val tocCreated: Long?,
    val tocUnavailable: Int?,
    val category: Int? = null,
    val subCategories: List<Int> = emptyList(),
    val alternateCover: Boolean,
    val volumeTitle: Boolean
)
