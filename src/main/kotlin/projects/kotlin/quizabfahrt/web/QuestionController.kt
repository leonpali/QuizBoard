package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.EQuestionTypes

@Controller
class QuestionController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{id}/question/{questionId}")
    fun question(
        model: Model,
        @PathVariable id: Int,
        @PathVariable questionId: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val question = questionService.getQuestion(session, id-1, questionId)
        model.addAttribute("question", question)
        model.addAttribute("boardId", id)

        if (question.type == EQuestionTypes.LINEUP) {
            val lineUp = question.text?.split(",")?.toList()
            model.addAttribute("lineUp", lineUp)
        }

        return when (question.type) {
            EQuestionTypes.TEXT -> "textQuestion"
            EQuestionTypes.IMAGE -> "imageQuestion"
            EQuestionTypes.LINEUP -> "lineUpQuestion"
        }
    }
}
