package projects.kotlin.quizabfahrt.application.session

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import projects.kotlin.quizabfahrt.model.Board
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

    @BeforeEach
    fun setUp() {
        service = QuizSessionService(
            uploadDir.toString(), Duration.ofHours(24), maxSessions = 2, maxPlayers = 3,
            events = ApplicationEventPublisher { publishedEvents.add(it) },
        )
        session = service.create(listOf(Board(0, emptyList())), previousHostToken = null) {}
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
        assertEquals(listOf("Anna"), service.hostState(session).players)
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
    fun `buzzing only works while a question is open`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)

        assertFalse(service.buzz(session, anna).buzzed)

        service.openQuestion(session, 0, 3)
        assertEquals(1, service.buzz(session, anna).position)

        service.closeBuzzing(session)
        val state = service.playerState(session, anna)
        assertFalse(state.buzzingOpen)
        assertTrue(state.buzzed)
    }

    @Test
    fun `each player can buzz once per question, in order`() {
        val (_, anna) = service.join(session.code, "Anna", "1.1.1.1", null)
        val (_, ben) = service.join(session.code, "Ben", "1.1.1.1", null)

        service.openQuestion(session, 0, 0)
        service.buzz(session, ben)
        service.buzz(session, anna)
        service.buzz(session, ben)
        assertEquals(listOf("Ben", "Anna"), service.hostState(session).buzzes)

        service.openQuestion(session, 0, 1)
        assertEquals(emptyList(), service.hostState(session).buzzes)
        assertEquals(1, service.buzz(session, anna).position)
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
}
