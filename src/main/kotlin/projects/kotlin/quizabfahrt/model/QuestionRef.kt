package projects.kotlin.quizabfahrt.model

// Indices are 0-based in code and 1-based in URLs; this is the only place that converts between them
data class QuestionRef(val board: Int, val category: Int, val question: Int) {

    fun toPath(suffix: String = ""): String =
        "/board/${board + 1}/category/${category + 1}/question/${question + 1}$suffix"

    fun getBoardPath(): String = "/board/${board + 1}"

    companion object {
        fun fromPath(board: Int, category: Int, question: Int) = QuestionRef(board - 1, category - 1, question - 1)

        fun getBoardIndex(boardNumber: Int) = boardNumber - 1
    }
}
