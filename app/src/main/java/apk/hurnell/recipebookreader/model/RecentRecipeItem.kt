package apk.hurnell.recipebookreader.model

import com.google.gson.annotations.SerializedName

data class RecentRecipeItem(
    @SerializedName("tocId") override val tocId: Long? = null,
    @SerializedName("recentRecipeId") val recentRecipeId: Long?,
    @SerializedName("title") override var title: String,
    @SerializedName("normalisedTitle") override var normalisedTitle: String,
    @SerializedName("bookTitle") override val bookTitle: String?,
    @SerializedName("bookLocation") override val bookLocation: String?,
    @SerializedName("bookId") override val bookId: Long?,
    @SerializedName("page") override val page: Int,
    @SerializedName("offset") override val offset: Int? = null,
    @SerializedName("scale") override val scale: Float = 1f,
    @SerializedName("translate") override val translate: Float = 0f,
    @SerializedName("isImage") var isImage: Boolean = false,
    @SerializedName("lastOpened") val lastOpened: Long?
) : BaseBookmarkTocItem
