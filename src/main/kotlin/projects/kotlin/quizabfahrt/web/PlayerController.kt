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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import projects.kotlin.quizabfahrt.application.session.PlayerState
import projects.kotlin.quizabfahrt.application.session.QuizSessionException
import projects.kotlin.quizabfahrt.application.session.QuizSessionService

@Controller
class PlayerController(private val sessions: QuizSessionService, private val broadcaster: QuizEventBroadcaster) {

    @GetMapping("/join")
    fun joinForm(
        model: Model,
        @RequestParam(required = false) code: String?,
        @CookieValue(SessionCookies.PLAYER, required = false) playerToken: String?,
    ): String {
        val joined = sessions.findPlayer(playerToken)
        if (joined != null && (code == null || code == joined.first.code)) return "redirect:/play"

        model.addAttribute("code", code ?: "")
        model.addAttribute("name", "")
        model.addAttribute("maxNameLength", QuizSessionService.MAX_NAME_LENGTH)
        return "join"
    }

    @PostMapping("/join")
    fun join(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam code: String,
        @RequestParam name: String,
        @CookieValue(SessionCookies.PLAYER, required = false) playerToken: String?,
    ): String {
        val (session, player) = try {
            sessions.join(code, name, request.remoteAddr, playerToken)
        } catch (e: QuizSessionException) {
            model.addAttribute("error", e.message)
            model.addAttribute("code", code)
            model.addAttribute("name", name)
            model.addAttribute("maxNameLength", QuizSessionService.MAX_NAME_LENGTH)
            return "join"
        }

        SessionCookies.set(request, response, SessionCookies.PLAYER, player.token, session.expiresAt)
        return "redirect:/play"
    }

    @GetMapping("/play")
    fun play(model: Model, @CookieValue(SessionCookies.PLAYER, required = false) playerToken: String?): String {
        val (session, player) = sessions.findPlayer(playerToken) ?: return "redirect:/join"
        model.addAttribute("code", session.code)
        model.addAttribute("name", player.name)
        return "play"
    }

    @GetMapping("/play/events")
    fun events(@CookieValue(SessionCookies.PLAYER, required = false) playerToken: String?): ResponseEntity<SseEmitter> {
        val (session, player) = sessions.findPlayer(playerToken) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return sseResponse(broadcaster.subscribePlayer(session, player))
    }

    @PostMapping("/play/buzz")
    fun buzz(@CookieValue(SessionCookies.PLAYER, required = false) playerToken: String?): ResponseEntity<PlayerState> {
        val (session, player) = sessions.findPlayer(playerToken) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(sessions.buzz(session, player))
    }
}
