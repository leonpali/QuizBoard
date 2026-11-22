package projects.kotlin.quizabfahrt.model

typealias QuestionContent = Content<EQuestionTypes>

typealias AnswerContent = Content<EAnswerTypes>

data class Question(
    val points: Int,
    val content: QuestionContent,
    val answer: AnswerContent,
    val input: EInputTypes = EInputTypes.DEFAULT,
) {

    fun getTypeKey(): String = content.type.key

    fun getTypeLabel(): String = content.type.label

    fun getAnswerTypeKey(): String = answer.type.key

    fun getInputLabel(): String = input.label

    fun hasInputValues(): Boolean = input.takesValue

    fun getValue(key: String): String? = content.getValue(key)

    fun getAnswerValue(key: String): String? = answer.getValue(key)

    fun getSummary(): String = content.getSummary()

    fun getAnswerSummary(): String = answer.getSummary()

    fun getFiles(): List<String> = content.getFiles() + answer.getFiles()
}
