package apk.hurnell.recipebookreader.helpers

import android.util.Log
import apk.hurnell.recipebookreader.model.Book
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GoogleBooksResponse(val items: List<BookItem>?)
data class BookItem(val volumeInfo: VolumeInfo)
data class VolumeInfo(val imageLinks: ImageLinks?)
data class ImageLinks(val thumbnail: String?)

interface GoogleBooksApi {
    @GET("volumes")
    suspend fun searchByQuery(@Query("q") query: String): GoogleBooksResponse
}

class GetCoverUrlHelper {


    suspend fun getAllAvailableCovers(book: Book): List<String> {
        val allUrls = mutableListOf<String>()

        if (!book.isbn.isNullOrBlank()) {
            val googleIsbnResults = fetchGoogleBooksByIsbn(book.isbn)
            allUrls.addAll(googleIsbnResults)
        }
        if (book.name != null && book.author != null) {
            allUrls.addAll(
                fetchRemoteCoverUrls(
                    book.name,
                    book.author
                )
            )
            allUrls.addAll(fetchGoogleBooksByTitleAuthor(book.name, book.author))
        }
        return allUrls.distinct()
    }

    suspend fun fetchGoogleBooksByIsbn(isbn: String): List<String> = withContext(Dispatchers.IO) {
        val cleanIsbn = isbn.replace("-", "").replace(" ", "")
        val query = "isbn:$cleanIsbn"
        return@withContext performGoogleSearch(query)
    }

    suspend fun fetchGoogleBooksByTitleAuthor(title: String, author: String): List<String> =
        withContext(Dispatchers.IO) {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            val encodedAuthor = URLEncoder.encode(author, StandardCharsets.UTF_8.toString())
            val query = "intitle:\"$encodedTitle\"+inauthor:\"$encodedAuthor\""
            return@withContext performGoogleSearch(query)
        }

    private suspend fun performGoogleSearch(query: String): List<String> {
        return try {
            val urlString = "https://www.googleapis.com/books/v1/volumes?q=$query"
            val connection = withContext(Dispatchers.IO) {
                URL(urlString).openConnection()
            } as HttpURLConnection
            val responseJson = connection.inputStream.bufferedReader().readText()

            val googleResponse = Gson().fromJson(responseJson, GoogleBooksResponse::class.java)


            googleResponse.items?.mapNotNull { item ->
                item.volumeInfo.imageLinks?.thumbnail?.replace("http://", "https://")
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("GOOGLE_BOOKS", "Search failed for query: $query", e)
            emptyList()
        }
    }

    suspend fun fetchRemoteCoverUrls(title: String, author: String): List<String> {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://openlibrary.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(OpenLibraryApi::class.java)
        return try {
            val response = api.searchBook(title, author)
            response.docs.mapNotNull { doc ->
                doc.coverId?.let { id -> "https://covers.openlibrary.org/b/id/$id-L.jpg" }
            }
        } catch (e: Exception) {
            Log.e("PDF_REPO", "Error fetching cover IDs", e)
            emptyList()
        }
    }

    suspend fun fetchLibraryThingCover(isbn: String): List<String> = withContext(Dispatchers.IO) {
        val cleanIsbn = isbn.replace("-", "").replace(" ", "")
        val url = "https://covers.librarything.com/devkey/YOUR_KEY/large/isbn/$cleanIsbn"

        return@withContext listOf(url)
    }

    suspend fun fetchGoogleBooksCovers(title: String, author: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                val encodedAuthor = URLEncoder.encode(author, StandardCharsets.UTF_8.toString())

                val urlString =
                    "https://www.googleapis.com/books/v1/volumes?q=intitle:\"$encodedTitle\"+inauthor:\"$encodedAuthor\""

                val connection = URL(urlString).openConnection() as HttpURLConnection
                val responseJson = connection.inputStream.bufferedReader().readText()

                val googleResponse = Gson().fromJson(responseJson, GoogleBooksResponse::class.java)

                googleResponse.items?.mapNotNull { item ->
                    item.volumeInfo.imageLinks?.thumbnail?.replace("http://", "https://")
                } ?: emptyList()

            } catch (e: Exception) {
                Log.e("GOOGLE_BOOKS", "Search failed", e)
                emptyList()
            }
        }

}