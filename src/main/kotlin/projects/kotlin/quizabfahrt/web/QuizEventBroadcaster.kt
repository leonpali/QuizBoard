package projects.kotlin.quizabfahrt.web

import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import projects.kotlin.quizabfahrt.application.session.Player
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.application.session.SessionChangedEvent
import projects.kotlin.quizabfahrt.application.session.SessionEndedEvent
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class QuizEventBroadcaster(private val sessions: QuizSessionService) {

    private class Subscriber(val emitter: SseEmitter, val player: Player?, val list: MutableList<Subscriber>)

    private val emitterTimeout = Duration.ofMinutes(30).toMillis()
    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>>()

    fun subscribeHost(session: QuizSession): SseEmitter = subscribe(session, null)

    fun subscribePlayer(session: QuizSession, player: Player): SseEmitter = subscribe(session, player)

    private fun subscribe(session: QuizSession, player: Player?): SseEmitter {
        val list = subscribers.computeIfAbsent(session.code) { CopyOnWriteArrayList() }
        val subscriber = Subscriber(SseEmitter(emitterTimeout), player, list)
        list.add(subscriber)

        val remove = Runnable { list.remove(subscriber) }
        subscriber.emitter.onCompletion(remove)
        subscriber.emitter.onTimeout(remove)
        subscriber.emitter.onError { remove.run() }

        sendState(session, subscriber)
        return subscriber.emitter
    }

    @EventListener
    fun onSessionChanged(event: SessionChangedEvent) {
        subscribers[event.session.code]?.forEach { sendState(event.session, it) }
    }

    @EventListener
    fun onSessionEnded(event: SessionEndedEvent) {
        subscribers.remove(event.code)?.forEach {
            send(it, SseEmitter.event().name("ended").data(""))
            runCatching { it.emitter.complete() }
        }
    }

    @Scheduled(fixedDelayString = "PT20S")
    fun heartbeat() {
        subscribers.values.flatten().forEach { send(it, SseEmitter.event().comment("ping")) }
    }

    private fun sendState(session: QuizSession, subscriber: Subscriber) {
        val state: Any = subscriber.player
            ?.let { sessions.playerState(session, it) }
            ?: sessions.hostState(session)
        send(subscriber, SseEmitter.event().name("state").data(state, MediaType.APPLICATION_JSON))
    }

    private fun send(subscriber: Subscriber, event: SseEmitter.SseEventBuilder) {
        try {
            subscriber.emitter.send(event)
        } catch (e: Exception) {
            subscriber.list.remove(subscriber)
        }
    }
}
