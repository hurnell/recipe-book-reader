package apk.hurnell.recipebookreader.model

data class TocItem(
    val tocId:Long,
    val bookTitle: String? = null,
    val bookLocation: String? = null,
    val parentId: Int? = null,
    val title: String,
    val hierarchy: String? = null,
    val page: Int,
    val level: Int,
    val offset: Float = 0f,
    val scale: Float = 1f,
    val translate: Float = 0f,
    val children: List<TocItem> = emptyList(),
    var isExpanded: Boolean = false
)