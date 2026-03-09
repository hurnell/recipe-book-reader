package apk.hurnell.recipebookreader.helpers

import retrofit2.http.GET
import retrofit2.http.Query

data class OpenLibraryResponse(
    val docs: List<BookDoc>
)

data class BookDoc(
    val coverId: Int?
)

interface OpenLibraryApi {
    @GET("search.json")
    suspend fun searchBook(
        @Query("title") title: String,
        @Query("author") author: String
    ): OpenLibraryResponse
}