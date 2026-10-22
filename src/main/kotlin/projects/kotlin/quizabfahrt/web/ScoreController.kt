package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.Outcome
import projects.kotlin.quizabfahrt.application.session.QuizSessionException
import projects.kotlin.quizabfahrt.application.session.QuizSessionService

@Controller
class ScoreController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{id}/question/{questionId}/score")
    fun score(
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
        model.addAttribute("penalty", question.points / 2)
        model.addAttribute("rows", sessions.scoringRows(session, id-1, questionId))
        return "score"
    }

    @PostMapping("/board/{id}/question/{questionId}/score")
    fun toggleResult(
        @PathVariable id: Int,
        @PathVariable questionId: Int,
        @RequestParam playerId: Int,
        @RequestParam outcome: Outcome,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val question = questionService.getQuestion(session, id-1, questionId)
        try {
            sessions.toggleResult(session, id-1, questionId, playerId, outcome, question.points)
        } catch (_: QuizSessionException) {
        }
        return "redirect:/board/$id/question/$questionId/score"
    }
}
