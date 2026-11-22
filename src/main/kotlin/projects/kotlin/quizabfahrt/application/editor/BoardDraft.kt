package projects.kotlin.quizabfahrt.application.editor

import projects.kotlin.quizabfahrt.application.service.parser.BoardWriter
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.Category
import projects.kotlin.quizabfahrt.model.Question
import java.time.Instant

class CategoryDraft(var name: String, val questions: MutableList<Question> = mutableListOf()) {

    fun isComplete() = questions.size == CompilationEngine.QUESTIONS_PER_CATEGORY

    fun toCategory() = Category(name, questions.toList())
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

    fun getQuestion(categoryIndex: Int, questionIndex: Int): Question =
        getCategory(categoryIndex).questions.getOrNull(questionIndex) ?: throw EditorItemNotFoundException()

    fun setQuestion(categoryIndex: Int, questionIndex: Int, question: Question) {
        getQuestion(categoryIndex, questionIndex)
        getCategory(categoryIndex).questions[questionIndex] = question
    }

    fun removeQuestion(categoryIndex: Int, questionIndex: Int) {
        getQuestion(categoryIndex, questionIndex)
        getCategory(categoryIndex).questions.removeAt(questionIndex)
    }

    fun getIncompleteCategories(): List<CategoryDraft> = categories.filter { !it.isComplete() }

    fun toText(): String = BoardWriter.write(categories.map { it.toCategory() })

    fun addFrom(board: Board) {
        board.categories.forEach { categories.add(CategoryDraft(it.name, it.questions.toMutableList())) }
    }
}

class BoardEditorException(message: String) : RuntimeException(message)

class NoDraftException : RuntimeException()

class EditorItemNotFoundException : RuntimeException()
