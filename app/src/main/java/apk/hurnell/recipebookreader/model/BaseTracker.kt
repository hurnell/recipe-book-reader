package apk.hurnell.recipebookreader.model

open class BaseTracker {
    fun getProperties(): Map<String, Any?> {
        return this::class
            .members
            .filterIsInstance<kotlin.reflect.KProperty1<Any, *>>()
            .associate { prop ->
                prop.name to prop.get(this)
            }
    }

    fun asString(): String {
        val properties = getProperties()
        var result = ""
        properties.forEach { (name, value) ->
            result = "$result $name = $value"
        }
        return result
    }
}