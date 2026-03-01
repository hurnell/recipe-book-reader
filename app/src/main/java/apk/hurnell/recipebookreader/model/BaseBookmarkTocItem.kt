package apk.hurnell.recipebookreader.model

interface  BaseBookmarkTocItem {
     val tocId: Long?
     val bookId: Long?
     val bookTitle: String?
     val bookLocation: String?
     val page: Int
     val offset: Float
     val scale: Float
     val translate: Float
}