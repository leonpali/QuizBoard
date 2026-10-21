package projects.kotlin.quizabfahrt.application.session

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.model.Board
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
            val player = synchronized(session) { session.players[token] } ?: continue
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

        val trimmedName = name.trim()
        val player = synchronized(session) {
            existingToken?.let { session.players[it] }?.let { return session to it }

            if (trimmedName.isEmpty() || trimmedName.length > MAX_NAME_LENGTH) {
                throw QuizSessionException("Der Name muss zwischen 1 und $MAX_NAME_LENGTH Zeichen lang sein.")
            }
            if (session.players.values.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                throw QuizSessionException("Der Name \"$trimmedName\" ist in diesem Quiz schon vergeben.")
            }
            if (session.players.size >= maxPlayers) {
                throw QuizSessionException("Das Quiz ist voll.")
            }
            Player(newToken(), trimmedName).also { session.players[it.token] = it }
        }
        events.publishEvent(SessionChangedEvent(session))
        return session to player
    }

    fun openQuestion(session: QuizSession, boardIndex: Int, questionId: Int) {
        synchronized(session) {
            session.currentQuestion = QuizSession.questionKey(boardIndex, questionId)
            session.buzzingOpen = true
        }
        events.publishEvent(SessionChangedEvent(session))
    }

    fun closeBuzzing(session: QuizSession) {
        val changed = synchronized(session) {
            session.buzzingOpen.also { session.buzzingOpen = false }
        }
        if (changed) events.publishEvent(SessionChangedEvent(session))
    }

    fun buzz(session: QuizSession, player: Player): PlayerState {
        val accepted = synchronized(session) {
            val key = session.currentQuestion
            if (!session.buzzingOpen || key == null) return@synchronized false
            val order = session.buzzes.getOrPut(key) { mutableListOf() }
            if (player in order) return@synchronized false
            order.add(player)
            true
        }
        if (accepted) events.publishEvent(SessionChangedEvent(session))
        return playerState(session, player)
    }

    fun hostState(session: QuizSession): HostState = synchronized(session) {
        HostState(
            code = session.code,
            players = session.players.values.map { it.name },
            buzzingOpen = session.buzzingOpen,
            buzzes = currentBuzzes(session).map { it.name },
        )
    }

    fun playerState(session: QuizSession, player: Player): PlayerState = synchronized(session) {
        val position = currentBuzzes(session).indexOf(player)
        PlayerState(
            name = player.name,
            buzzingOpen = session.buzzingOpen,
            buzzed = position >= 0,
            position = if (position >= 0) position + 1 else null,
        )
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

    private fun currentBuzzes(session: QuizSession): List<Player> =
        session.currentQuestion?.let { session.buzzes[it] }.orEmpty()

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
