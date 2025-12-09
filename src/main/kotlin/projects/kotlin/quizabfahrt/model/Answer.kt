package projects.kotlin.quizabfahrt.model

data class Answer(
    val id: Int,
    val type: EAnswerTypes,
    val text: String?,
    val img: String?
)
