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
import projects.kotlin.quizabfahrt.model.QuestionRef

@Controller
class ScoreController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{board}/category/{category}/question/{question}/score")
    fun score(
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

        model.addAttribute("ref", ref)
        model.addAttribute("question", current)
        model.addAttribute("penalty", current.points / 2)
        model.addAttribute("rows", session.getScoringRows(ref))
        return "score"
    }

    @PostMapping("/board/{board}/category/{category}/question/{question}/score")
    fun toggleResult(
        @PathVariable board: Int,
        @PathVariable category: Int,
        @PathVariable question: Int,
        @RequestParam playerId: Int,
        @RequestParam outcome: Outcome,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val ref = QuestionRef.fromPath(board, category, question)
        val current = questionService.getQuestion(session, ref)
        try {
            sessions.toggleResult(session, ref, playerId, outcome, current.points)
        } catch (_: QuizSessionException) {
        }
        return "redirect:${ref.toPath("/score")}"
    }
}
