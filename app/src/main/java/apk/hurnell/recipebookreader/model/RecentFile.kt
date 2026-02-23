package apk.hurnell.recipebookreader.model


data class RecentFile(
    val lastOpened: Long?,
    val location: String
) {
    override fun toString(): String = location
}