package apk.hurnell.recipebookreader.model

import com.google.gson.annotations.SerializedName

data class BookmarkItem(
    @SerializedName("tocId") override val tocId: Long? = null,
    @SerializedName("bookmarkId") val bookmarkId: Long?,
    @SerializedName("title") override var title: String,
    @SerializedName("bookTitle") override val bookTitle: String?,
    @SerializedName("bookLocation") override val bookLocation: String?,
    @SerializedName("bookId") override val bookId: Long?,
    @SerializedName("page") override val page: Int,
    @SerializedName("offset") override val offset: Int? = null,
    @SerializedName("scale") override val scale: Float = 1f,
    @SerializedName("translate") override val translate: Float = 0f,
    @SerializedName("isImage") var isImage: Boolean = false,
    @SerializedName("position") var position: Int? = null
) : BaseBookmarkTocItem
