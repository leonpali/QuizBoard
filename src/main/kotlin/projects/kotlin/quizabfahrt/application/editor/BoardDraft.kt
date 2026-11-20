package projects.kotlin.quizabfahrt.application.editor

import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import java.time.Instant

class QuestionDraft(
    var type: EQuestionTypes = EQuestionTypes.TEXT,
    var text: String? = "Neue Frage",
    var img: String? = null,
    var points: Int = 100,
    var answerType: EAnswerTypes = EAnswerTypes.TEXT,
    var answerText: String? = "Antwort",
    var answerImg: String? = null,
) {
    fun getTypeKey(): String = type.key

    fun isImage() = type == EQuestionTypes.IMAGE

    fun isAnswerImage() = answerType == EAnswerTypes.IMAGE
}

class CategoryDraft(var name: String, val questions: MutableList<QuestionDraft> = mutableListOf()) {

    fun isComplete() = questions.size == CompilationEngine.QUESTIONS_PER_CATEGORY
}

enum class MoveDirection(val offset: Int) { UP(-1), DOWN(1) }

fun <T> MutableList<T>.move(index: Int, direction: MoveDirection) {
    if (index !in indices) throw EditorItemNotFoundException()
    val target = index + direction.offset
    if (target in indices) add(target, removeAt(index))
}

class BoardDraft(val token: String, val creatorIp: String, var fileName: String, var lastChanged: Instant) {

    val categories = mutableListOf<CategoryDraft>()

    fun getCategory(index: Int): CategoryDraft = categories.getOrNull(index) ?: throw EditorItemNotFoundException()

    fun getQuestion(categoryIndex: Int, questionIndex: Int): QuestionDraft =
        getCategory(categoryIndex).questions.getOrNull(questionIndex) ?: throw EditorItemNotFoundException()

    fun getIncompleteCategories(): List<CategoryDraft> = categories.filter { !it.isComplete() }

    fun toText(): String = buildString {
        categories.forEachIndexed { c, category ->
            appendLine("category_${c + 1}:")
            appendLine("    name: ${category.name}")
            appendLine("    questions:")
            category.questions.forEachIndexed { q, question ->
                appendLine("        question_${q + 1}:")
                appendLine("            type: ${question.type.key}")
                appendLine("            text: ${question.text ?: "null"}")
                appendLine("            img: ${question.img ?: "null"}")
                appendLine("            points: ${question.points}")
                appendLine("            answer:")
                appendLine("                type: ${question.answerType.key}")
                appendLine("                text: ${question.answerText ?: "null"}")
                appendLine("                img: ${question.answerImg ?: "null"}")
            }
        }
    }

    fun addFrom(board: Board) {
        board.categories.forEach { category ->
            categories.add(CategoryDraft(category.name, category.questions.map {
                QuestionDraft(it.type, it.text, it.img, it.points, it.answer.type, it.answer.text, it.answer.img)
            }.toMutableList()))
        }
    }
}

class BoardEditorException(message: String) : RuntimeException(message)

class NoDraftException : RuntimeException()

class EditorItemNotFoundException : RuntimeException()
