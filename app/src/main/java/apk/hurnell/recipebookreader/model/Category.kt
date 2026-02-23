package apk.hurnell.recipebookreader.model

data class Category(
    val id: Long,
    val category: String
) {
    override fun toString(): String = category
}