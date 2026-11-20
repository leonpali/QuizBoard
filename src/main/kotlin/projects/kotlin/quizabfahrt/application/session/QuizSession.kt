package projects.kotlin.quizabfahrt.application.session

import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.QuestionRef
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
    private val players = LinkedHashMap<String, Player>()
    private var currentQuestion: QuestionRef? = null
    private var buzzingOpen = false
    private val askedQuestions = HashSet<QuestionRef>()
    private val buzzes = HashMap<QuestionRef, MutableList<Player>>()
    private val results = HashMap<QuestionRef, HashMap<Int, QuestionResult>>()
    private var nextPlayerId = 1

    fun isExpired(now: Instant = Instant.now()) = now.isAfter(expiresAt)

    @Synchronized
    fun findPlayer(token: String): Player? = players[token]

    @Synchronized
    fun hasPlayers(): Boolean = players.isNotEmpty()

    @Synchronized
    fun addPlayer(name: String, token: String, maxPlayers: Int): Player {
        if (players.values.any { it.name.equals(name, ignoreCase = true) }) {
            throw QuizSessionException("Der Name \"$name\" ist in diesem Quiz schon vergeben.")
        }
        if (players.size >= maxPlayers) throw QuizSessionException("Das Quiz ist voll.")
        return Player(nextPlayerId++, token, name).also { players[token] = it }
    }

    @Synchronized
    fun openQuestion(ref: QuestionRef) {
        askedQuestions.add(ref)
        currentQuestion = ref
        buzzingOpen = true
    }

    @Synchronized
    fun closeBuzzing(): Boolean = buzzingOpen.also { buzzingOpen = false }

    @Synchronized
    fun isAsked(ref: QuestionRef): Boolean = ref in askedQuestions

    @Synchronized
    fun buzz(player: Player): Boolean {
        val ref = currentQuestion
        if (!buzzingOpen || ref == null) return false
        val order = buzzes.getOrPut(ref) { mutableListOf() }
        if (player in order) return false
        order.add(player)
        return true
    }

    @Synchronized
    fun toggleResult(ref: QuestionRef, playerId: Int, outcome: Outcome, points: Int) {
        if (players.values.none { it.id == playerId }) throw QuizSessionException("Unbekannter Teilnehmer.")
        val questionResults = results.getOrPut(ref) { HashMap() }
        if (questionResults[playerId]?.outcome == outcome) {
            questionResults.remove(playerId)
        } else {
            val delta = if (outcome == Outcome.CORRECT) points else -points / 2
            questionResults[playerId] = QuestionResult(outcome, delta)
        }
    }

    @Synchronized
    fun getScoringRows(ref: QuestionRef): List<ScoringRow> {
        val buzzOrder = buzzes[ref].orEmpty()
        val questionResults = results[ref].orEmpty()
        return (buzzOrder + players.values.filter { it !in buzzOrder }).map { player ->
            ScoringRow(
                playerId = player.id,
                name = player.name,
                buzzPosition = buzzOrder.indexOf(player).takeIf { it >= 0 }?.plus(1),
                outcome = questionResults[player.id]?.outcome,
                score = getScore(player),
            )
        }
    }

    @Synchronized
    fun getHostState() = HostState(
        code = code,
        players = players.values.map { PlayerScore(it.name, getScore(it)) }.sortedByDescending { it.score },
        buzzingOpen = buzzingOpen,
        buzzes = getCurrentBuzzes().map { it.name },
    )

    @Synchronized
    fun getPlayerState(player: Player): PlayerState {
        val position = getCurrentBuzzes().indexOf(player)
        return PlayerState(
            name = player.name,
            score = getScore(player),
            buzzingOpen = buzzingOpen,
            buzzed = position >= 0,
            position = if (position >= 0) position + 1 else null,
        )
    }

    @Synchronized
    fun getRanking(): List<RankedPlayer> {
        val sorted = players.values.map { PlayerScore(it.name, getScore(it)) }.sortedByDescending { it.score }
        return sorted.map { player ->
            RankedPlayer(sorted.indexOfFirst { it.score == player.score } + 1, player.name, player.score)
        }
    }

    private fun getScore(player: Player): Int = results.values.sumOf { it[player.id]?.points ?: 0 }

    private fun getCurrentBuzzes(): List<Player> = currentQuestion?.let { buzzes[it] }.orEmpty()
}

data class PlayerScore(val name: String, val score: Int)

data class RankedPlayer(val place: Int, val name: String, val score: Int)

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
) {
    fun isCorrect() = outcome == Outcome.CORRECT

    fun isWrong() = outcome == Outcome.WRONG
}

data class SessionChangedEvent(val session: QuizSession)

data class SessionEndedEvent(val code: String)

class QuizSessionException(message: String) : RuntimeException(message)

class NoSessionException : RuntimeException()
