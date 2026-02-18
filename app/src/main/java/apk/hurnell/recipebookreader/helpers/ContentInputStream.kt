package apk.hurnell.recipebookreader.helpers

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.artifex.mupdf.fitz.SeekableInputStream
import java.io.IOException
import java.io.InputStream

class ContentInputStream(
    private val cr: ContentResolver,
    private val uri: Uri,
    private var length: Long = -1
) : SeekableInputStream {

    private var inputStream: InputStream? = null
    private var p: Long = 0
    private var mustReopenStream = false
    private val APP = "ContentInputStream"

    init {
        reopenStream()
    }

    @Throws(IOException::class)
    private fun reopenStream() {
        inputStream?.close()
        inputStream = cr.openInputStream(uri)
        p = 0
        mustReopenStream = false
    }

    @Throws(IOException::class)
    override fun seek(offset: Long, whence: Int): Long {
        val newp = when (whence) {
            SEEK_SET -> offset
            SEEK_CUR -> p + offset
            SEEK_END -> if (length >= 0) length + offset else p
            else -> p
        }

        if (newp < p) {
            if (!mustReopenStream) {
                try {
                    inputStream?.skip(newp - p)
                } catch (_: IOException) {
                    Log.i(APP, "Cannot skip backwards, reopening stream")
                    mustReopenStream = true
                }
            }
            if (mustReopenStream) {
                reopenStream()
                inputStream?.skip(newp)
            }
        } else if (newp > p) {
            inputStream?.skip(newp - p)
        }
        p = newp
        return p
    }

    @Throws(IOException::class)
    override fun position(): Long = p

    @Throws(IOException::class)
    override fun read(buf: ByteArray): Int {
        val n = inputStream?.read(buf) ?: -1
        if (n > 0) p += n
        else if (n < 0 && length < 0) length = p
        return n
    }

    companion object {
        const val SEEK_SET = 0
        const val SEEK_CUR = 1
        const val SEEK_END = 2
    }
}
