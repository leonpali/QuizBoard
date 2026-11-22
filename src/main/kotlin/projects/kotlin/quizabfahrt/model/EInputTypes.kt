package projects.kotlin.quizabfahrt.model

enum class EInputTypes(override val key: String, override val label: String, val maxLength: Int = 0) : KeyedType {
    BUZZER("buzzer", "Buzzer"),
    TEXT("text", "Texteingabe", maxLength = 100);

    val takesValue: Boolean get() = maxLength > 0

    fun cleanValue(value: String?): String? = value?.trim()?.take(maxLength)?.takeIf { it.isNotEmpty() }

    fun accepts(value: String?): Boolean = !takesValue || value != null

    companion object {
        val DEFAULT = BUZZER
    }
}
