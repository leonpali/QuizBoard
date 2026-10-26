package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.QuizSessionService

@Controller
class BoardController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{id}")
    fun board(
        model: Model,
        @PathVariable id: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val categories = questionService.getBoard(session, id-1).categories
        sessions.closeBuzzing(session)

        val isLastBoard = id >= session.boards.size
        model.addAttribute("categories", categories)
        model.addAttribute("id", id-1)
        model.addAttribute("nextUrl", if (isLastBoard) "/host/scoreboard" else "/board/${id+1}")
        model.addAttribute("nextLabel", if (isLastBoard) "Zum Endstand" else "Nächstes Board")
        return "board"
    }

    @PostMapping("/board/{id}/{questionId}")
    fun chooseQuestion(
        model: Model,
        @PathVariable id: Int,
        @PathVariable questionId: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        questionService.changeAskedStatus(session, id, questionId)
        sessions.openQuestion(session, id, questionId)
        return "redirect:/board/${id+1}/question/$questionId"
    }
}
