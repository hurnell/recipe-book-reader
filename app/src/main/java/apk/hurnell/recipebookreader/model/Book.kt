package apk.hurnell.recipebookreader.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class Book : RealmObject {
    @PrimaryKey
    var id: Long = 0L
    var title: String? = null
    var content: String? = null
}