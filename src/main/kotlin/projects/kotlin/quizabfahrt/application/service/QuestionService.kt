package projects.kotlin.quizabfahrt.application.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.Question

@Service
class QuestionService {

    fun getBoard(session: QuizSession, boardId: Int): Board {
        return session.boards.getOrNull(boardId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Board ${boardId + 1} existiert nicht")
    }

    fun changeAskedStatus(session: QuizSession, boardId: Int, questionId: Int) {
        getQuestion(session, boardId, questionId).asked = true
    }

    fun getQuestion(session: QuizSession, boardId: Int, questionId: Int): Question {
        return getBoard(session, boardId)
            .categories.getOrNull(calculateCategoryIndex(questionId))
            ?.questions?.getOrNull(calculateQuestionIndex(questionId))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Frage $questionId existiert nicht")
    }

    private fun calculateCategoryIndex(questionId: Int): Int {
        return questionId / CompilationEngine.QUESTIONS_PER_CATEGORY
    }

    private fun calculateQuestionIndex(questionId: Int): Int {
        return questionId % CompilationEngine.QUESTIONS_PER_CATEGORY
    }
}
