package apk.hurnell.recipebookreader.model

data class TocItem(
    val title: String,
    val page: Int,
    val level: Int,
    val offset: Float = 0f,
    val scale: Float = 1f,
    val translate: Float = 0f,
    val children: List<TocItem> = emptyList(),
    var isExpanded: Boolean = false
)