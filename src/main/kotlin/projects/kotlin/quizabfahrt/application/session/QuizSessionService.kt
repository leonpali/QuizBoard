package projects.kotlin.quizabfahrt.application.session

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.model.Board
import projects.kotlin.quizabfahrt.model.EInputTypes
import projects.kotlin.quizabfahrt.model.Question
import projects.kotlin.quizabfahrt.model.QuestionRef
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class QuizSessionService(
    @Value("\${quiz.upload-dir}") uploadDir: String,
    @Value("\${quiz.ttl}") private val ttl: Duration,
    @Value("\${quiz.max-sessions}") private val maxSessions: Int,
    @Value("\${quiz.max-players}") private val maxPlayers: Int,
    private val events: ApplicationEventPublisher,
) {

    companion object {
        const val MAX_NAME_LENGTH = 20
        private const val MAX_FAILED_JOINS = 10
        private val FAILED_JOIN_WINDOW: Duration = Duration.ofMinutes(10)
    }

    private val log = LoggerFactory.getLogger(QuizSessionService::class.java)
    private val uploadRoot: Path = Paths.get(uploadDir).toAbsolutePath().normalize()
    private val sessionsByCode = ConcurrentHashMap<String, QuizSession>()
    private val failedJoins = ConcurrentHashMap<String, MutableList<Instant>>()
    private val random = SecureRandom()

    init {
        deleteRecursively(uploadRoot)
        Files.createDirectories(uploadRoot)
    }

    fun create(boards: List<Board>, previousHostToken: String?, storeImages: (Path) -> Unit): QuizSession {
        findByHostToken(previousHostToken)?.let(::end)
        removeExpired()
        if (sessionsByCode.size >= maxSessions) {
            throw QuizSessionException("Es laufen gerade zu viele Quizze gleichzeitig. Bitte später erneut versuchen.")
        }

        var session: QuizSession
        do {
            val code = (100000 + random.nextInt(900000)).toString()
            session = QuizSession(code, newToken(), boards, uploadRoot.resolve(code), Instant.now().plus(ttl))
        } while (sessionsByCode.putIfAbsent(code, session) != null)

        try {
            Files.createDirectories(session.imageDir)
            storeImages(session.imageDir)
        } catch (e: Exception) {
            end(session)
            throw e
        }
        log.info("Quiz {} created with {} board(s)", session.code, boards.size)
        return session
    }

    fun findByHostToken(token: String?): QuizSession? {
        if (token == null) return null
        return sessionsByCode.values.firstOrNull { it.hostToken == token && !it.isExpired() }
    }

    fun requireHost(token: String?): QuizSession = findByHostToken(token) ?: throw NoSessionException()

    fun findPlayer(token: String?): Pair<QuizSession, Player>? {
        if (token == null) return null
        for (session in sessionsByCode.values) {
            if (session.isExpired()) continue
            val player = session.findPlayer(token) ?: continue
            return session to player
        }
        return null
    }

    fun join(code: String, name: String, clientIp: String, existingToken: String?): Pair<QuizSession, Player> {
        if (isThrottled(clientIp)) {
            throw QuizSessionException("Zu viele falsche Codes. Bitte in ein paar Minuten erneut versuchen.")
        }

        val session = sessionsByCode[code.trim()]?.takeUnless { it.isExpired() }
        if (session == null) {
            recordFailedJoin(clientIp)
            throw QuizSessionException("Kein Quiz mit diesem Code gefunden.")
        }

        existingToken?.let { session.findPlayer(it) }?.let { return session to it }

        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > MAX_NAME_LENGTH) {
            throw QuizSessionException("Der Name muss zwischen 1 und $MAX_NAME_LENGTH Zeichen lang sein.")
        }
        val player = session.addPlayer(trimmedName, newToken(), maxPlayers)
        events.publishEvent(SessionChangedEvent(session))
        return session to player
    }

    fun openQuestion(session: QuizSession, ref: QuestionRef, input: EInputTypes) {
        session.openQuestion(ref, input)
        events.publishEvent(SessionChangedEvent(session))
    }

    fun closeInput(session: QuizSession) {
        if (session.closeInput()) events.publishEvent(SessionChangedEvent(session))
    }

    fun submit(session: QuizSession, player: Player, value: String?): PlayerState {
        if (session.submit(player, value)) events.publishEvent(SessionChangedEvent(session))
        return session.getPlayerState(player)
    }

    fun toggleResult(session: QuizSession, ref: QuestionRef, playerId: Int, outcome: Outcome, question: Question) {
        session.toggleResult(ref, playerId, outcome, question)
        events.publishEvent(SessionChangedEvent(session))
    }

    fun end(session: QuizSession) {
        if (sessionsByCode.remove(session.code, session)) {
            deleteRecursively(session.imageDir)
            events.publishEvent(SessionEndedEvent(session.code))
            log.info("Quiz {} ended", session.code)
        }
    }

    @Scheduled(fixedDelayString = "PT5M")
    fun removeExpired() {
        val now = Instant.now()
        sessionsByCode.values.filter { it.isExpired(now) }.forEach(::end)
        failedJoins.values.forEach { attempts -> synchronized(attempts) { attempts.removeIf { it.isBefore(now - FAILED_JOIN_WINDOW) } } }
        failedJoins.entries.removeIf { synchronized(it.value) { it.value.isEmpty() } }
    }

    private fun isThrottled(ip: String): Boolean {
        val attempts = failedJoins[ip] ?: return false
        val since = Instant.now() - FAILED_JOIN_WINDOW
        return synchronized(attempts) { attempts.count { it.isAfter(since) } >= MAX_FAILED_JOINS }
    }

    private fun recordFailedJoin(ip: String) {
        val attempts = failedJoins.computeIfAbsent(ip) { mutableListOf() }
        synchronized(attempts) { attempts.add(Instant.now()) }
    }

    private fun newToken() = UUID.randomUUID().toString()

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
