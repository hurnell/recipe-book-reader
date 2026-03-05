package apk.hurnell.recipebookreader.model

data class TocDisplayItem(
    val bookTitle: String?,
    val title: String,
    val hierarchy: String?,
    val page: Int,
    val level: Int,
    val tocId: Long
)