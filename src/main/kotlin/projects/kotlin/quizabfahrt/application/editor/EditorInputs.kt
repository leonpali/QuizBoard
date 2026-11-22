package projects.kotlin.quizabfahrt.application.editor

import projects.kotlin.quizabfahrt.model.Content
import projects.kotlin.quizabfahrt.model.ContentField
import projects.kotlin.quizabfahrt.model.UsesFields
import projects.kotlin.quizabfahrt.model.QuestionSetting
import projects.kotlin.quizabfahrt.model.QuestionSettings

data class ContentInput(val typeKey: String, private val values: Map<String, String?>) {

    fun isType(key: String) = typeKey == key

    fun getValue(key: String): String? = values[key]

    fun getValues(): Map<ContentField, String?> = ContentField.entries.associateWith { values[it.key] }

    companion object {
        fun of(content: Content<*>) = ContentInput(content.type.key, content.values.mapKeys { it.key.key })

        fun fromForm(params: Map<String, String>) =
            ContentInput(params["type"].orEmpty(), ContentField.entries.associate { it.key to params[it.key] })
    }
}

data class SettingsInput(val values: Map<String, String?>) {

    fun getValue(key: String): String? = values[key]

    fun isValue(key: String, value: String) = values[key] == value

    companion object {
        val NONE = SettingsInput(emptyMap())

        fun of(settings: QuestionSettings) = SettingsInput(settings.getFormatted())

        fun fromForm(params: Map<String, String>) = SettingsInput(QuestionSetting.entries.associate { it.key to params[it.key] })
    }
}

data class FormField(private val field: ContentField, val usedBy: String) {

    fun getKey() = field.key

    fun getLabel() = field.label

    fun isFile() = field.isFile

    fun isMultiline() = field.isMultiline

    fun getMaxLength() = field.maxLength

    fun getExtensions() = field.extensions.joinToString()

    companion object {
        fun of(types: List<UsesFields>): List<FormField> = ContentField.entries.mapNotNull { field ->
            val users = types.filter { field in it.fields }
            if (users.isEmpty()) null else FormField(field, users.joinToString { it.label })
        }
    }
}
