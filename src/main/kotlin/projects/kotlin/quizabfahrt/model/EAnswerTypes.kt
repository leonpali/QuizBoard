package projects.kotlin.quizabfahrt.model

enum class EAnswerTypes(
    override val key: String,
    override val label: String,
    override val fields: Set<ContentField>,
    override val hint: String? = null,
) : ContentType {
    TEXT("text", "Text", setOf(ContentField.TEXT)),
    IMAGE("img", "Bild", setOf(ContentField.IMAGE)),
    TEXTIMAGE("textimage", "Bild und Text", setOf(ContentField.TEXT, ContentField.IMAGE)),
}
