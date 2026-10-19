package projects.kotlin.quizabfahrt.application.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import projects.kotlin.quizabfahrt.model.Board

@Service
class QuizStore {

    @Volatile
    private var currentBoards: List<Board> = emptyList()

    val boards: List<Board>
        get() = currentBoards

    fun replace(newBoards: List<Board>) {
        currentBoards = newBoards
    }

    fun getBoard(index: Int): Board {
        return currentBoards.getOrNull(index)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Board ${index + 1} existiert nicht")
    }
}
