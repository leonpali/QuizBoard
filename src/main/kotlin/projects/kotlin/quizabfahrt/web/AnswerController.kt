package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.EAnswerTypes

@Controller
class AnswerController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{id}/question/{questionId}/answer")
    fun answer(
        model: Model,
        @PathVariable id: Int,
        @PathVariable questionId: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val question = questionService.getQuestion(session, id-1, questionId)
        sessions.closeBuzzing(session)

        model.addAttribute("boardId", id)
        model.addAttribute("question", question)

        return when (question.answer.type) {
            EAnswerTypes.TEXT -> "textAnswer"
            EAnswerTypes.IMAGE -> "imageAnswer"
            EAnswerTypes.TEXTIMAGE -> "textImageAnswer"
        }
    }
}
