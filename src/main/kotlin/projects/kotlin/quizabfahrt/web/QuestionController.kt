package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import projects.kotlin.quizabfahrt.model.QuestionRef

@Controller
class QuestionController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{board}/category/{category}/question/{question}")
    fun question(
        model: Model,
        @PathVariable board: Int,
        @PathVariable category: Int,
        @PathVariable question: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val ref = QuestionRef.fromPath(board, category, question)
        val current = questionService.getQuestion(session, ref)
        model.addAttribute("question", current)
        model.addAttribute("ref", ref)

        if (current.type == EQuestionTypes.LINEUP) {
            val lineUp = current.text?.split(",")?.toList()
            model.addAttribute("lineUp", lineUp)
        }

        return when (current.type) {
            EQuestionTypes.TEXT -> "textQuestion"
            EQuestionTypes.IMAGE -> "imageQuestion"
            EQuestionTypes.LINEUP -> "lineUpQuestion"
        }
    }
}
