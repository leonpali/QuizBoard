package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.QuestionRef

@Controller
class AnswerController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{board}/category/{category}/question/{question}/answer")
    fun answer(
        model: Model,
        @PathVariable board: Int,
        @PathVariable category: Int,
        @PathVariable question: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val ref = QuestionRef.fromPath(board, category, question)
        val current = questionService.getQuestion(session, ref)
        sessions.closeBuzzing(session)

        model.addAttribute("question", current)
        model.addAttribute(
            "nextUrl",
            if (session.hasPlayers()) ref.toPath("/score") else ref.getBoardPath(),
        )

        return when (current.answer.type) {
            EAnswerTypes.TEXT -> "textAnswer"
            EAnswerTypes.IMAGE -> "imageAnswer"
            EAnswerTypes.TEXTIMAGE -> "textImageAnswer"
        }
    }
}
