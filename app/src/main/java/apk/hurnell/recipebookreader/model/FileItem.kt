package apk.hurnell.recipebookreader.model

import java.io.File

data class FileItem(
    val file: File,
    val displayName: String,
    val opened:Boolean? = null
)