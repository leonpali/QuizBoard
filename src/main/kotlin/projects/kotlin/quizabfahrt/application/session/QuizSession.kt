package projects.kotlin.quizabfahrt.application.session

import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.EInputTypes
import projects.kotlin.quizabfahrt.model.QuestionRef
import java.nio.file.Path
import java.time.Instant

class Player(val id: Int, val token: String, val name: String)

enum class Outcome { CORRECT, WRONG }

data class QuestionResult(val outcome: Outcome, val points: Int)

data class Submission(val player: Player, val value: String?)

class QuizSession(
    val code: String,
    val hostToken: String,
    val boards: List<Board>,
    val imageDir: Path,
    val expiresAt: Instant,
) {
    private val players = LinkedHashMap<String, Player>()
    private var currentQuestion: QuestionRef? = null
    private var currentInput = EInputTypes.DEFAULT
    private var inputOpen = false
    private val askedQuestions = HashSet<QuestionRef>()
    private val submissions = HashMap<QuestionRef, MutableList<Submission>>()
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
    fun openQuestion(ref: QuestionRef, input: EInputTypes) {
        askedQuestions.add(ref)
        currentQuestion = ref
        currentInput = input
        inputOpen = true
    }

    @Synchronized
    fun closeInput(): Boolean = inputOpen.also { inputOpen = false }

    @Synchronized
    fun isAsked(ref: QuestionRef): Boolean = ref in askedQuestions

    @Synchronized
    fun submit(player: Player, value: String?): Boolean {
        val ref = currentQuestion
        if (!inputOpen || ref == null) return false
        val cleaned = currentInput.cleanValue(value)
        if (!currentInput.accepts(cleaned)) return false
        val questionSubmissions = submissions.getOrPut(ref) { mutableListOf() }
        if (questionSubmissions.any { it.player == player }) return false
        questionSubmissions.add(Submission(player, cleaned))
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
        val questionSubmissions = submissions[ref].orEmpty()
        val submitted = questionSubmissions.mapIndexed { index, it -> getScoringRow(ref, it.player, index + 1, it.value) }
        val waiting = players.values.filter { player -> questionSubmissions.none { it.player == player } }
        return submitted + waiting.map { getScoringRow(ref, it, null, null) }
    }

    @Synchronized
    fun getHostState() = HostState(
        code = code,
        players = players.values.map { PlayerScore(it.name, getScore(it)) }.sortedByDescending { it.score },
        input = currentInput.label,
        inputOpen = inputOpen,
        submissions = getCurrentSubmissions().map { it.player.name },
    )

    @Synchronized
    fun getPlayerState(player: Player): PlayerState {
        val current = getCurrentSubmissions()
        val index = current.indexOfFirst { it.player == player }
        return PlayerState(
            name = player.name,
            score = getScore(player),
            input = currentInput.key,
            inputOpen = inputOpen,
            submitted = index >= 0,
            position = if (index >= 0) index + 1 else null,
            value = current.getOrNull(index)?.value,
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

    private fun getCurrentSubmissions(): List<Submission> = currentQuestion?.let { submissions[it] }.orEmpty()

    private fun getScoringRow(ref: QuestionRef, player: Player, position: Int?, value: String?) =
        ScoringRow(player.id, player.name, position, value, results[ref]?.get(player.id)?.outcome, getScore(player))
}

data class PlayerScore(val name: String, val score: Int)

data class RankedPlayer(val place: Int, val name: String, val score: Int)

data class HostState(
    val code: String,
    val players: List<PlayerScore>,
    val input: String,
    val inputOpen: Boolean,
    val submissions: List<String>,
)

data class PlayerState(
    val name: String,
    val score: Int,
    val input: String,
    val inputOpen: Boolean,
    val submitted: Boolean,
    val position: Int?,
    val value: String?,
)

data class ScoringRow(
    val playerId: Int,
    val name: String,
    val position: Int?,
    val value: String?,
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
