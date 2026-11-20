package projects.kotlin.quizabfahrt.model

data class Question(
    val type: EQuestionTypes,
    val text: String?,
    val img: String?,
    val points: Int,
    val answer: Answer
) {
    fun getTypeKey(): String = type.key

    fun getAnswerTypeKey(): String = answer.type.key

    fun getLineup(): List<String> = text.orEmpty().split(",").map { it.trim() }

    fun getAnswerText(): String? = answer.text

    fun getAnswerImg(): String? = answer.img
}
