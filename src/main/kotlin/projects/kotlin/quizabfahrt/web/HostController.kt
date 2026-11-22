package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.application.session.RankedPlayer

data class PodiumStep(val place: Int, val players: List<RankedPlayer>)

@Controller
class HostController(private val sessions: QuizSessionService, private val broadcaster: QuizEventBroadcaster) {

    @GetMapping("/host")
    fun lobby(model: Model, @HostSession session: QuizSession): String {
        sessions.closeInput(session)

        model.addAttribute("code", session.code)
        model.addAttribute("joinUrl", ServletUriComponentsBuilder.fromCurrentContextPath().path("/join").toUriString())
        model.addAttribute("boardCount", session.boards.size)
        model.addAttribute("players", session.getHostState().players)
        return "host"
    }

    @GetMapping("/host/scoreboard")
    fun scoreboard(model: Model, @HostSession session: QuizSession): String {
        sessions.closeInput(session)
        val ranking = session.getRanking()

        val podium = listOf(2, 1, 3).mapNotNull { place ->
            ranking.filter { it.place == place }.takeIf { it.isNotEmpty() }?.let { PodiumStep(place, it) }
        }
        model.addAttribute("podium", podium)
        model.addAttribute("rest", ranking.filter { it.place > 3 })
        return "scoreboard"
    }

    @GetMapping("/host/events")
    fun events(@HostSession session: QuizSession?): ResponseEntity<SseEmitter> {
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return sseResponse(broadcaster.subscribeHost(session))
    }

    @PostMapping("/host/end")
    fun end(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @HostSession session: QuizSession?,
    ): String {
        session?.let(sessions::end)
        SessionCookies.clear(request, response, SessionCookies.HOST)
        return "redirect:/"
    }
}

fun sseResponse(emitter: SseEmitter): ResponseEntity<SseEmitter> =
    ResponseEntity.ok().header("X-Accel-Buffering", "no").body(emitter)
