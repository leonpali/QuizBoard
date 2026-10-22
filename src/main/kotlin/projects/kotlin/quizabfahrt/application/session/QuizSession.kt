package projects.kotlin.quizabfahrt.application.session

import projects.kotlin.quizabfahrt.model.Board
import java.nio.file.Path
import java.time.Instant

class Player(val id: Int, val token: String, val name: String)

enum class Outcome { CORRECT, WRONG }

data class QuestionResult(val outcome: Outcome, val points: Int)

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

    val results = HashMap<String, HashMap<Int, QuestionResult>>()

    var nextPlayerId = 1

    fun isExpired(now: Instant = Instant.now()) = now.isAfter(expiresAt)

    fun getScore(player: Player): Int = results.values.sumOf { it[player.id]?.points ?: 0 }

    fun getCurrentBuzzes(): List<Player> = currentQuestion?.let { buzzes[it] }.orEmpty()

    companion object {
        fun questionKey(boardIndex: Int, questionId: Int) = "$boardIndex:$questionId"
    }
}

data class PlayerScore(val name: String, val score: Int)

data class HostState(
    val code: String,
    val players: List<PlayerScore>,
    val buzzingOpen: Boolean,
    val buzzes: List<String>,
)

data class PlayerState(
    val name: String,
    val score: Int,
    val buzzingOpen: Boolean,
    val buzzed: Boolean,
    val position: Int?,
)

data class ScoringRow(
    val playerId: Int,
    val name: String,
    val buzzPosition: Int?,
    val outcome: Outcome?,
    val score: Int,
)

data class SessionChangedEvent(val session: QuizSession)

data class SessionEndedEvent(val code: String)

class QuizSessionException(message: String) : RuntimeException(message)

class NoSessionException : RuntimeException()
