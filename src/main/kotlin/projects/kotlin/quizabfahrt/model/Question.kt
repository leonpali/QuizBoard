package projects.kotlin.quizabfahrt.model

data class Question(
    val id: Int,
    val type: EQuestionTypes,
    val text: String?,
    val img: String?,
    val points: Int,
    val answer: Answer
)
