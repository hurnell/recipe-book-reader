package apk.hurnell.recipebookreader.helpers

import android.content.ContentResolver
import android.net.Uri
import com.artifex.mupdf.fitz.SeekableInputStream
import java.io.InputStream

class PdfStreamer(
    private val resolver: ContentResolver,
    private val uri: Uri
) : SeekableInputStream {
    private var stream: InputStream? = null
    private var pos: Long = 0
    private val totalSize: Long by lazy {
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }

    private fun reopen() {
        try {
            stream?.close()
            stream = resolver.openInputStream(uri)?.apply { skip(pos) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun read(b: ByteArray): Int {
        if (stream == null) reopen()
        return stream?.read(b)?.also { if (it > 0) pos += it } ?: -1
    }

    override fun seek(off: Long, whence: Int): Long {
        pos = when (whence) {
            SeekableInputStream.SEEK_SET -> off
            SeekableInputStream.SEEK_CUR -> pos + off
            SeekableInputStream.SEEK_END -> totalSize + off
            else -> pos
        }
        reopen()
        return pos
    }

    override fun position(): Long = pos
    fun size(): Long = totalSize
}