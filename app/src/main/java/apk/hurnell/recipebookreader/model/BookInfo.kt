package apk.hurnell.recipebookreader.model

data class BookInfo(
    val sha: String,
    val name: String,
    val mainCategory: String? = null,
    val subCategories: List<String> = emptyList(),
    val author: String? = null
)