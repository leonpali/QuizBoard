package projects.kotlin.quizabfahrt.model

enum class EQuestionTypes(
    override val key: String,
    override val label: String,
    override val fields: Set<ContentField>,
    override val hint: String? = null,
) : ContentType {
    TEXT("text", "Text", setOf(ContentField.TEXT)),
    IMAGE("img", "Bild", setOf(ContentField.IMAGE)),
    LINEUP("lineup", "Lineup", setOf(ContentField.TEXT), "Einträge im Text mit Komma trennen, sie werden nacheinander aufgedeckt"),
}
