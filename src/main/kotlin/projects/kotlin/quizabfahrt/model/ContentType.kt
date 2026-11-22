package projects.kotlin.quizabfahrt.model

interface KeyedType {
    val key: String
    val label: String
}

interface ContentType : KeyedType {
    val fields: Set<ContentField>
    val hint: String?
}

inline fun <reified T> typeOf(key: String?): T? where T : Enum<T>, T : KeyedType =
    enumValues<T>().firstOrNull { it.key == key }
