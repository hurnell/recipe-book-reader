package apk.hurnell.recipebookreader.model

data class CategoryItem(
    val category: String,
    val count: Int?
) {
    override fun toString(): String = category
}