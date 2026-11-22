package projects.kotlin.quizabfahrt.application.editor

import projects.kotlin.quizabfahrt.model.Content
import projects.kotlin.quizabfahrt.model.ContentField
import projects.kotlin.quizabfahrt.model.ContentType
import projects.kotlin.quizabfahrt.model.Question

data class ContentInput(
    val typeKey: String,
    private val values: Map<String, String?>,
    val points: String? = null,
    val inputKey: String? = null,
) {

    fun isType(key: String) = typeKey == key

    fun isInput(key: String) = inputKey == key

    fun getValue(key: String): String? = values[key]

    fun getValues(): Map<ContentField, String?> = ContentField.entries.associateWith { values[it.key] }

    companion object {
        fun of(content: Content<*>) = ContentInput(content.type.key, content.values.mapKeys { it.key.key })

        fun of(question: Question) = of(question.content).copy(points = question.points.toString(), inputKey = question.input.key)

        fun fromForm(params: Map<String, String>) = ContentInput(
            params["type"].orEmpty(),
            ContentField.entries.associate { it.key to params[it.key] },
            params["points"],
            params["input"],
        )
    }
}

data class FormField(private val field: ContentField, val usedBy: String) {

    fun getKey() = field.key

    fun getLabel() = field.label

    fun isFile() = field.isFile

    fun isMultiline() = field.isMultiline

    fun getExtensions() = field.extensions.joinToString()

    companion object {
        fun of(types: List<ContentType>): List<FormField> = ContentField.entries.mapNotNull { field ->
            val users = types.filter { field in it.fields }
            if (users.isEmpty()) null else FormField(field, users.joinToString { it.label })
        }
    }
}
