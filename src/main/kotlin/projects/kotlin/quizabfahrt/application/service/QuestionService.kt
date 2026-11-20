package projects.kotlin.quizabfahrt.application.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.Question
import projects.kotlin.quizabfahrt.model.QuestionRef

@Service
class QuestionService {

    fun getBoard(session: QuizSession, boardIndex: Int): Board {
        return session.boards.getOrNull(boardIndex)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Board ${boardIndex + 1} existiert nicht")
    }

    fun getQuestion(session: QuizSession, ref: QuestionRef): Question {
        return getBoard(session, ref.board)
            .categories.getOrNull(ref.category)
            ?.questions?.getOrNull(ref.question)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Frage existiert nicht")
    }
}
