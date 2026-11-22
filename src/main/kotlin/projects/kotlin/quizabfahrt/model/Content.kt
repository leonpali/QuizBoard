package projects.kotlin.quizabfahrt.model

class ContentException(message: String) : RuntimeException(message)

@ConsistentCopyVisibility
data class Content<T : ContentType> private constructor(val type: T, val values: Map<ContentField, String>) {

    fun getValue(key: String): String? = values.entries.firstOrNull { it.key.key == key }?.value

    fun getFiles(): List<String> = values.filterKeys { it.isFile }.values.toList()

    fun getSummary(): String = type.fields.mapNotNull { values[it] }.joinToString(" · ")

    companion object {
        fun <T : ContentType> of(type: T, values: Map<ContentField, String?>): Content<T> {
            val present = ContentField.entries
                .mapNotNull { field -> values[field]?.trim()?.takeIf { it.isNotEmpty() }?.let { field to it } }
                .toMap()
            type.requireFields(present.keys, "den Typ")
            return Content(type, present)
        }
    }
}
