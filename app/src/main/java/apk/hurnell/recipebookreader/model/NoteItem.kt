package apk.hurnell.recipebookreader.model

import com.google.gson.annotations.SerializedName

data class NoteItem(
    @SerializedName("noteId") val noteId: Long? = null,
    @SerializedName("bookId") val bookId: Long?,
    @SerializedName("bookLocation") val bookLocation: String? = null,
    @SerializedName("page") val page: Int,
    @SerializedName("x") val x: Float,
    @SerializedName("y") val y: Float,
    @SerializedName("noteText") var noteText: String
)
