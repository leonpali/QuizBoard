package projects.kotlin.quizabfahrt.model

interface ContentType {
    val key: String
    val label: String
    val fields: Set<ContentField>
    val hint: String?
}

inline fun <reified T> contentTypeOf(key: String?): T? where T : Enum<T>, T : ContentType =
    enumValues<T>().firstOrNull { it.key == key }
