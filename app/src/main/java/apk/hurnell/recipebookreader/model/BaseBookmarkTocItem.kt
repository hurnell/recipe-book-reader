package apk.hurnell.recipebookreader.model

interface  BaseBookmarkTocItem {
     val tocId: Long?
     val bookId: Long?
     val bookTitle: String?
     var title: String
     var normalisedTitle: String
     val bookLocation: String?
     val page: Int
     val offset: Int?
     val scale: Float
     val translate: Float
}
