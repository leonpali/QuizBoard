package projects.kotlin.quizabfahrt.application.session

import projects.kotlin.quizabfahrt.model.Board
import java.nio.file.Path
import java.time.Instant

class Player(val token: String, val name: String)

class QuizSession(
    val code: String,
    val hostToken: String,
    val boards: List<Board>,
    val imageDir: Path,
    val expiresAt: Instant,
) {
    val players = LinkedHashMap<String, Player>()

    var currentQuestion: String? = null

    var buzzingOpen = false

    val buzzes = HashMap<String, MutableList<Player>>()

    fun isExpired(now: Instant = Instant.now()) = now.isAfter(expiresAt)

    companion object {
        fun questionKey(boardIndex: Int, questionId: Int) = "$boardIndex:$questionId"
    }
}

data class HostState(
    val code: String,
    val players: List<String>,
    val buzzingOpen: Boolean,
    val buzzes: List<String>,
)

data class PlayerState(
    val name: String,
    val buzzingOpen: Boolean,
    val buzzed: Boolean,
    val position: Int?,
)

data class SessionChangedEvent(val session: QuizSession)

data class SessionEndedEvent(val code: String)

class QuizSessionException(message: String) : RuntimeException(message)

class NoSessionException : RuntimeException()
