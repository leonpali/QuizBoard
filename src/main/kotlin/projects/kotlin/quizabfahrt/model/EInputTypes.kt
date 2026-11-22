package projects.kotlin.quizabfahrt.model

enum class EInputTypes(
    override val key: String,
    override val label: String,
    val maxLength: Int = 0,
    override val fields: Set<ContentField> = emptySet(),
) : UsesFields {
    BUZZER("buzzer", "Buzzer"),
    TEXT("text", "Texteingabe", maxLength = 100);

    val takesValue: Boolean get() = maxLength > 0

    // Returns the value to store, or null if it isn't a valid answer to this question
    open fun readValue(value: String, question: Question): String? = value.trim().take(maxLength).takeIf { it.isNotEmpty() }

    // Sent to the participants' phones, so it must never contain the answer
    open fun getPlayerData(question: Question): Map<String, Any> = emptyMap()

    companion object {
        val DEFAULT = BUZZER
    }
}
