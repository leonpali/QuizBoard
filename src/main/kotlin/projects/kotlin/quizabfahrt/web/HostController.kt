package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.application.session.RankedPlayer

data class PodiumStep(val place: Int, val players: List<RankedPlayer>)

@Controller
class HostController(private val sessions: QuizSessionService, private val broadcaster: QuizEventBroadcaster) {

    @GetMapping("/host")
    fun lobby(model: Model, @CookieValue(SessionCookies.HOST, required = false) hostToken: String?): String {
        val session = sessions.requireHost(hostToken)
        sessions.closeBuzzing(session)

        model.addAttribute("code", session.code)
        model.addAttribute("joinUrl", ServletUriComponentsBuilder.fromCurrentContextPath().path("/join").toUriString())
        model.addAttribute("boardCount", session.boards.size)
        model.addAttribute("players", sessions.hostState(session).players)
        return "host"
    }

    @GetMapping("/host/scoreboard")
    fun scoreboard(model: Model, @CookieValue(SessionCookies.HOST, required = false) hostToken: String?): String {
        val session = sessions.requireHost(hostToken)
        sessions.closeBuzzing(session)
        val ranking = sessions.ranking(session)

        val podium = listOf(2, 1, 3).mapNotNull { place ->
            ranking.filter { it.place == place }.takeIf { it.isNotEmpty() }?.let { PodiumStep(place, it) }
        }
        model.addAttribute("podium", podium)
        model.addAttribute("rest", ranking.filter { it.place > 3 })
        return "scoreboard"
    }

    @GetMapping("/host/events")
    fun events(@CookieValue(SessionCookies.HOST, required = false) hostToken: String?): ResponseEntity<SseEmitter> {
        val session = sessions.findByHostToken(hostToken) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return sseResponse(broadcaster.subscribeHost(session))
    }

    @PostMapping("/host/end")
    fun end(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        sessions.findByHostToken(hostToken)?.let(sessions::end)
        SessionCookies.clear(request, response, SessionCookies.HOST)
        return "redirect:/"
    }
}

fun sseResponse(emitter: SseEmitter): ResponseEntity<SseEmitter> =
    ResponseEntity.ok().header("X-Accel-Buffering", "no").body(emitter)
