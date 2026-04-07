package apk.hurnell.recipebookreader.model

import com.google.gson.annotations.SerializedName

data class TocItem(
    @SerializedName("tocId") override val tocId: Long,
    @SerializedName("bookId") override val bookId: Long? = null,
    @SerializedName("bookTitle") override val bookTitle: String? = null,
    @SerializedName("bookLocation") override val bookLocation: String? = null,
    @SerializedName("parentId") val parentId: Int? = null,
    @SerializedName("title") override var title: String,
    @SerializedName("normalisedTitle") override var normalisedTitle: String,
    @SerializedName("hierarchy") val hierarchy: String? = null,
    @SerializedName("page") override val page: Int,
    @SerializedName("offset") override val offset: Int? = null,
    @SerializedName("level") val level: Int,
    @SerializedName("bookmarkId") val bookmarkId: Int? = null,
    @SerializedName("scale") override val scale: Float = 1f,
    @SerializedName("translate") override val translate: Float = 0f,
    @SerializedName("children") val children: List<TocItem> = emptyList(),
    @SerializedName("isExpanded") var isExpanded: Boolean = false
) : BaseBookmarkTocItem {
    fun toBookmarkItem(): BookmarkItem {
        return BookmarkItem(
            tocId = tocId,
            bookmarkId = bookmarkId?.toLong(),
            title = title,
            normalisedTitle = normalisedTitle,
            bookTitle = bookTitle,
            bookLocation = bookLocation,
            bookId = bookId,
            page = page,
            isImage = hierarchy == "Images",
            offset = null,
            scale = scale,
            translate = translate
        )
    }
}
