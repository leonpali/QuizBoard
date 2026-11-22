package projects.kotlin.quizabfahrt.application.session

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.Content
import projects.kotlin.quizabfahrt.model.ContentField
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import projects.kotlin.quizabfahrt.model.Question
import projects.kotlin.quizabfahrt.model.QuestionSettings
import projects.kotlin.quizabfahrt.model.EInputTypes
import projects.kotlin.quizabfahrt.model.QuestionRef
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QuizSessionServiceTest {

    @TempDir
    lateinit var uploadDir: Path

    private val publishedEvents = mutableListOf<Any>()
    private lateinit var service: QuizSessionService
    private lateinit var session: QuizSession

    private fun question(points: Int = 100, input: EInputTypes = EInputTypes.BUZZER) = Question(
        Content.of(EQuestionTypes.TEXT, mapOf(ContentField.TEXT to "Frage")),
        Content.of(EAnswerTypes.TEXT, mapOf(ContentField.TEXT to "Antwort")),
        QuestionSettings.of(mapOf("points" to points.toString(), "input" to input.key)),
    )

    @BeforeEach
    fun setUp() {
        service = QuizSessionService(
            uploadDir.toString(), Duration.ofHours(24), maxSessions = 2, maxPlayers = 3,
            events = ApplicationEventPublisher { publishedEvents.add(it) },
        )
        session = service.create(listOf(Board(emptyList())), previousHostToken = null) {}
    }

    @Test
    fun `creates a session with a six digit code`() {
        assertTrue(session.code.matches(Regex("\\d{6}")))
        assertSame(session, service.findByHostToken(session.hostToken))
    }

    @Test
    fun `creating a new quiz ends the one the device was hosting`() {
        val newSession = service.create(emptyList(), session.hostToken) {}

        assertNull(service.findByHostToken(session.hostToken))
        assertSame(newSession, service.findByHostToken(newSession.hostToken))
        assertTrue(SessionEndedEvent(session.code) in publishedEvents)
    }

    @Test
    fun `players can join with the code and rejoin from the same device`() {
        val (_, anna) = service.join(session.code, " Anna ", "1.1.1.1", null)
        val (_, again) = service.join(session.code, "Other name", "1.1.1.1", anna.token)

        assertEquals("Anna", anna.name)
        assertSame(anna, again)
        assertEquals(listOf(PlayerScore("Anna", 0)), session.getHostState().players)
    }

    @Test
    fun `joining rejects wrong codes, taken names and full quizzes`() {
        val wrongCode = if (session.code == "999999") "111111" else "999999"
        assertFailsWith<QuizSessionException> { service.join(wrongCode, "Anna", "1.1.1.1", null) }

        service.join(session.code, "Anna", "1.1.1.1", null)
        assertFailsWith<QuizSessionException> { service.join(session.code, "anna", "1.1.1.1", null) }

        service.join(session.code, "Ben", "1.1.1.1", null)
        service.join(session.code, "Cem", "1.1.1.1", null)
        assertFailsWith<QuizSessionException> { service.join(session.code, "Dana", "1.1.1.1", null) }
    }

    @Test
    fun `too many wrong codes from one address are throttled`() {
        val wrongCode = if (session.code == "999999") "111111" else "999999"
        repeat(10) { runCatching { service.join(wrongCode, "Anna", "6.6.6.6", null) } }

        val e = assertFailsWith<QuizSessionException> { service.join(session.code, "Anna", "6.6.6.6", null) }
        assertTrue(e.message!!.contains("Zu viele"))
        service.join(session.code, "Anna", "1.1.1.1", null)
    }

    @Test
    fun `input only works while a question is open`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)

        assertFalse(service.submit(session, anna, null).submitted)

        service.openQuestion(session, QuestionRef(0, 0, 3), question(input = EInputTypes.BUZZER))
        assertEquals(1, service.submit(session, anna, null).position)

        service.closeInput(session)
        val state = session.getPlayerState(anna)
        assertFalse(state.inputOpen)
        assertTrue(state.submitted)
    }

    @Test
    fun `opening a question marks it as asked`() {
        val ref = QuestionRef(0, 1, 4)
        assertFalse(session.isAsked(ref))

        service.openQuestion(session, ref, question(input = EInputTypes.BUZZER))

        assertTrue(session.isAsked(ref))
        assertFalse(session.isAsked(QuestionRef(0, 1, 3)))
    }

    @Test
    fun `each player can buzz once per question, in order`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)
        val (_, ben) = service.join(session.code, "Ben", "1.1.1.1", null)

        service.openQuestion(session, QuestionRef(0, 0, 0), question(input = EInputTypes.BUZZER))
        service.submit(session, ben, null)
        service.submit(session, anna, null)
        service.submit(session, ben, null)
        assertEquals(listOf("Ben", "Anna"), session.getHostState().submissions)

        service.openQuestion(session, QuestionRef(0, 0, 1), question(input = EInputTypes.BUZZER))
        assertEquals(emptyList(), session.getHostState().submissions)
        assertEquals(1, service.submit(session, anna, null).position)
    }

    @Test
    fun `text input needs a value and keeps it for scoring`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)
        val (_, ben) = service.join(session.code, "Ben", "1.1.1.1", null)
        val ref = QuestionRef(0, 0, 0)
        service.openQuestion(session, ref, question(input = EInputTypes.TEXT))

        assertFalse(service.submit(session, anna, "   ").submitted)
        assertEquals("Paris", service.submit(session, anna, " Paris ").value)
        assertEquals("Paris", service.submit(session, anna, "Lyon").value)
        service.submit(session, ben, "x".repeat(500))

        val rows = session.getScoringRows(ref)
        assertEquals(listOf("Paris", "x".repeat(EInputTypes.TEXT.maxLength)), rows.map { it.value })
        assertEquals("Texteingabe", session.getHostState().inputLabel)
        assertEquals("text", session.getPlayerState(anna).inputKey)
    }

    @Test
    fun `buzzers ignore submitted values`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)
        service.openQuestion(session, QuestionRef(0, 0, 0), question(input = EInputTypes.BUZZER))

        val state = service.submit(session, anna, "Paris")

        assertTrue(state.submitted)
        assertNull(state.value)
    }

    @Test
    fun `ending a session removes it and its images`() {
        val imageDir = session.imageDir
        service.end(session)

        assertNull(service.findByHostToken(session.hostToken))
        assertFalse(imageDir.toFile().exists())
        assertFailsWith<QuizSessionException> { service.join(session.code, "Anna", "1.1.1.1", null) }
    }

    @Test
    fun `codes are unique across sessions`() {
        val other = service.create(emptyList(), null) {}
        assertNotEquals(session.code, other.code)
    }

    @Test
    fun `correct answers add the points, wrong answers deduct half`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)
        val (_, ben) = service.join(session.code, "Ben", "1.1.1.1", null)

        service.toggleResult(session, QuestionRef(0, 0, 0), anna.id, Outcome.WRONG, question(300))
        service.toggleResult(session, QuestionRef(0, 0, 0), ben.id, Outcome.CORRECT, question(300))
        service.toggleResult(session, QuestionRef(0, 0, 1), anna.id, Outcome.CORRECT, question(500))

        assertEquals(350, session.getPlayerState(anna).score)
        assertEquals(300, session.getPlayerState(ben).score)
        assertEquals(listOf(PlayerScore("Anna", 350), PlayerScore("Ben", 300)), session.getHostState().players)
    }

    @Test
    fun `several participants can get points for the same question`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)
        val (_, ben) = service.join(session.code, "Ben", "1.1.1.1", null)

        service.toggleResult(session, QuestionRef(0, 0, 0), anna.id, Outcome.CORRECT, question(200))
        service.toggleResult(session, QuestionRef(0, 0, 0), ben.id, Outcome.CORRECT, question(200))

        assertEquals(200, session.getPlayerState(anna).score)
        assertEquals(200, session.getPlayerState(ben).score)
    }

    @Test
    fun `choosing the same outcome again removes it, choosing the other one replaces it`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)

        service.toggleResult(session, QuestionRef(0, 0, 0), anna.id, Outcome.CORRECT, question(400))
        service.toggleResult(session, QuestionRef(0, 0, 0), anna.id, Outcome.WRONG, question(400))
        assertEquals(-200, session.getPlayerState(anna).score)

        service.toggleResult(session, QuestionRef(0, 0, 0), anna.id, Outcome.WRONG, question(400))
        assertEquals(0, session.getPlayerState(anna).score)
        assertNull(session.getScoringRows(QuestionRef(0, 0, 0)).single().outcome)
    }

    @Test
    fun `scoring rows list submissions first, in order`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)
        val (_, ben) = service.join(session.code, "Ben", "1.1.1.1", null)
        val (_, cem) = service.join(session.code, "Cem", "1.1.1.1", null)

        service.openQuestion(session, QuestionRef(0, 0, 2), question(input = EInputTypes.BUZZER))
        service.submit(session, cem, null)
        service.submit(session, ben, null)
        service.toggleResult(session, QuestionRef(0, 0, 2), ben.id, Outcome.CORRECT, question(100))

        val rows = session.getScoringRows(QuestionRef(0, 0, 2))
        assertEquals(listOf("Cem", "Ben", "Anna"), rows.map { it.name })
        assertEquals(listOf(1, 2, null), rows.map { it.position })
        assertEquals(listOf(null, Outcome.CORRECT, null), rows.map { it.outcome })
        assertEquals(anna.id, rows.last().playerId)
    }

    private fun useQuizWithRoomForTen() {
        service = QuizSessionService(
            uploadDir.resolve("large").toString(), Duration.ofHours(24), maxSessions = 2, maxPlayers = 10,
            events = ApplicationEventPublisher { },
        )
        session = service.create(emptyList(), null) {}
    }

    private fun joinWithScore(name: String, points: Int) {
        val (_, player) = service.join(session.code, name, "1.1.1.1", null)
        val ref = QuestionRef(0, 0, player.id)
        if (points > 0) service.toggleResult(session, ref, player.id, Outcome.CORRECT, question(points))
        if (points < 0) service.toggleResult(session, ref, player.id, Outcome.WRONG, question(-points * 2))
    }

    @Test
    fun `ranking orders by score without ties`() {
        useQuizWithRoomForTen()
        joinWithScore("Anna", 100)
        joinWithScore("Ben", 300)
        joinWithScore("Cem", 200)

        assertEquals(
            listOf(RankedPlayer(1, "Ben", 300), RankedPlayer(2, "Cem", 200), RankedPlayer(3, "Anna", 100)),
            session.getRanking(),
        )
    }

    @Test
    fun `tied participants share a place and the next places are skipped`() {
        useQuizWithRoomForTen()
        joinWithScore("Anna", 300)
        joinWithScore("Ben", 300)
        joinWithScore("Cem", 200)
        joinWithScore("Dana", 200)
        joinWithScore("Emil", 100)

        assertEquals(listOf(1, 1, 3, 3, 5), session.getRanking().map { it.place })
    }

    @Test
    fun `three tied for first leaves out second and third place`() {
        useQuizWithRoomForTen()
        joinWithScore("Anna", 500)
        joinWithScore("Ben", 500)
        joinWithScore("Cem", 500)
        joinWithScore("Dana", -50)

        assertEquals(
            listOf(RankedPlayer(1, "Anna", 500), RankedPlayer(1, "Ben", 500), RankedPlayer(1, "Cem", 500), RankedPlayer(4, "Dana", -50)),
            session.getRanking(),
        )
    }

    @Test
    fun `results for unknown participants are rejected`() {
        assertFailsWith<QuizSessionException> { service.toggleResult(session, QuestionRef(0, 0, 0), 42, Outcome.CORRECT, question(100)) }
    }
}
