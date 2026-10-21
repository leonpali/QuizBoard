package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import projects.kotlin.quizabfahrt.application.service.QuizService
import projects.kotlin.quizabfahrt.application.session.QuizSessionException
import projects.kotlin.quizabfahrt.application.session.QuizSessionService

@Controller
class HomeController(private val quizService: QuizService, private val sessions: QuizSessionService) {

    @GetMapping("/")
    fun home(
        model: Model,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
        @CookieValue(SessionCookies.PLAYER, required = false) playerToken: String?,
    ): String {
        model.addAttribute("hostedCode", sessions.findByHostToken(hostToken)?.code)
        model.addAttribute("joinedCode", sessions.findPlayer(playerToken)?.first?.code)
        model.addAttribute("bundledImages", quizService.bundledImages)
        return "index"
    }

    @PostMapping("/quiz")
    fun createQuiz(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
        @CookieValue(SessionCookies.PLAYER, required = false) playerToken: String?,
        @RequestParam("boards", required = false) boards: List<MultipartFile>?,
        @RequestParam("images", required = false) images: List<MultipartFile>?,
    ): String {
        val session = try {
            quizService.createQuiz(boards.orEmpty(), images.orEmpty(), hostToken)
        } catch (e: QuizSessionException) {
            model.addAttribute("errors", listOf(e.message))
            return home(model, hostToken, playerToken)
        }

        SessionCookies.set(request, response, SessionCookies.HOST, session.hostToken, session.expiresAt)
        return "redirect:/host"
    }
}
