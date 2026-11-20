package projects.kotlin.quizabfahrt.model

data class Question(
    val type: EQuestionTypes,
    val text: String?,
    val img: String?,
    val points: Int,
    val answer: Answer
) {
    fun getAnswerText(): String? = answer.text

    fun getAnswerImg(): String? = answer.img
}
