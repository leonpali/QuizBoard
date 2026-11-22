package projects.kotlin.quizabfahrt.application.session

import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.EInputTypes
import projects.kotlin.quizabfahrt.model.Question
import projects.kotlin.quizabfahrt.model.QuestionRef
import java.nio.file.Path
import java.time.Instant

class Player(val id: Int, val token: String, val name: String)

enum class Outcome { CORRECT, WRONG }

class QuizSession(
    val code: String,
    val hostToken: String,
    val boards: List<Board>,
    val imageDir: Path,
    val expiresAt: Instant,
) {
    private val players = LinkedHashMap<String, Player>()
    private val askedQuestions = HashSet<QuestionRef>()
    private val input = QuestionInput()
    private val scores = Scores()
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
    fun openQuestion(ref: QuestionRef, inputType: EInputTypes) {
        askedQuestions.add(ref)
        input.open(ref, inputType)
    }

    @Synchronized
    fun closeInput(): Boolean = input.close()

    @Synchronized
    fun isAsked(ref: QuestionRef): Boolean = ref in askedQuestions

    @Synchronized
    fun submit(player: Player, value: String?): Boolean = input.submit(player, value)

    @Synchronized
    fun toggleResult(ref: QuestionRef, playerId: Int, outcome: Outcome, question: Question) {
        val player = players.values.firstOrNull { it.id == playerId } ?: throw QuizSessionException("Unbekannter Teilnehmer.")
        scores.toggle(ref, player, outcome, question)
    }

    @Synchronized
    fun getScoringRows(ref: QuestionRef): List<ScoringRow> {
        val submissions = input.getSubmissions(ref)
        val submitted = submissions.mapIndexed { index, it -> getScoringRow(ref, it.player, index + 1, it.value) }
        val waiting = players.values.filter { player -> submissions.none { it.player == player } }
        return submitted + waiting.map { getScoringRow(ref, it, null, null) }
    }

    @Synchronized
    fun getHostState() = HostState(
        code = code,
        players = getPlayerScores(),
        inputLabel = input.type.label,
        inputOpen = input.isOpen,
        submissions = input.getCurrentSubmissions().map { it.player.name },
    )

    @Synchronized
    fun getPlayerState(player: Player): PlayerState {
        val submissions = input.getCurrentSubmissions()
        val index = submissions.indexOfFirst { it.player == player }
        return PlayerState(
            name = player.name,
            score = scores.getScore(player),
            inputKey = input.type.key,
            inputOpen = input.isOpen,
            submitted = index >= 0,
            position = if (index >= 0) index + 1 else null,
            value = submissions.getOrNull(index)?.value,
        )
    }

    @Synchronized
    fun getRanking(): List<RankedPlayer> {
        val sorted = getPlayerScores()
        return sorted.map { player ->
            RankedPlayer(sorted.indexOfFirst { it.score == player.score } + 1, player.name, player.score)
        }
    }

    private fun getPlayerScores(): List<PlayerScore> =
        players.values.map { PlayerScore(it.name, scores.getScore(it)) }.sortedByDescending { it.score }

    private fun getScoringRow(ref: QuestionRef, player: Player, position: Int?, value: String?) =
        ScoringRow(player.id, player.name, position, value, scores.getOutcome(ref, player), scores.getScore(player))
}

private data class Submission(val player: Player, val value: String?)

private class QuestionInput {
    private var currentQuestion: QuestionRef? = null
    private val submissions = HashMap<QuestionRef, MutableList<Submission>>()

    var type = EInputTypes.DEFAULT
        private set
    var isOpen = false
        private set

    fun open(ref: QuestionRef, input: EInputTypes) {
        currentQuestion = ref
        type = input
        isOpen = true
    }

    fun close(): Boolean = isOpen.also { isOpen = false }

    fun submit(player: Player, value: String?): Boolean {
        val ref = currentQuestion
        if (!isOpen || ref == null) return false
        val cleaned = type.cleanValue(value)
        if (!type.accepts(cleaned)) return false
        val questionSubmissions = submissions.getOrPut(ref) { mutableListOf() }
        if (questionSubmissions.any { it.player == player }) return false
        questionSubmissions.add(Submission(player, cleaned))
        return true
    }

    fun getSubmissions(ref: QuestionRef): List<Submission> = submissions[ref].orEmpty()

    fun getCurrentSubmissions(): List<Submission> = currentQuestion?.let(::getSubmissions).orEmpty()
}

private data class QuestionResult(val outcome: Outcome, val points: Int)

private class Scores {
    private val results = HashMap<QuestionRef, HashMap<Int, QuestionResult>>()

    fun toggle(ref: QuestionRef, player: Player, outcome: Outcome, question: Question) {
        val questionResults = results.getOrPut(ref) { HashMap() }
        if (questionResults[player.id]?.outcome == outcome) {
            questionResults.remove(player.id)
        } else {
            val points = if (outcome == Outcome.CORRECT) question.points else -question.getPenalty()
            questionResults[player.id] = QuestionResult(outcome, points)
        }
    }

    fun getScore(player: Player): Int = results.values.sumOf { it[player.id]?.points ?: 0 }

    fun getOutcome(ref: QuestionRef, player: Player): Outcome? = results[ref]?.get(player.id)?.outcome
}

data class PlayerScore(val name: String, val score: Int)

data class RankedPlayer(val place: Int, val name: String, val score: Int)

data class HostState(
    val code: String,
    val players: List<PlayerScore>,
    val inputLabel: String,
    val inputOpen: Boolean,
    val submissions: List<String>,
)

data class PlayerState(
    val name: String,
    val score: Int,
    val inputKey: String,
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
