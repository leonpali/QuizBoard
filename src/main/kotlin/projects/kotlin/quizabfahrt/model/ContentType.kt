package projects.kotlin.quizabfahrt.model

interface KeyedType {
    val key: String
    val label: String
}

interface UsesFields : KeyedType {
    val fields: Set<ContentField>

    fun requireFields(present: Set<ContentField>, kind: String) {
        val missing = fields - present
        if (missing.isNotEmpty()) throw ContentException("Für $kind „$label“ fehlt: ${missing.joinToString { it.label }}")
    }
}

interface ContentType : UsesFields {
    val hint: String?
}

inline fun <reified T> typeOf(key: String?): T? where T : Enum<T>, T : KeyedType =
    enumValues<T>().firstOrNull { it.key == key }
