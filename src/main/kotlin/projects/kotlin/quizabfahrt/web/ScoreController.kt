package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.Outcome
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.application.session.QuizSessionException
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.QuestionRef

@Controller
class ScoreController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{board}/category/{category}/question/{question}/score")
    fun score(model: Model, @HostSession session: QuizSession, ref: QuestionRef): String {
        val question = questionService.getQuestion(session, ref)
        sessions.closeBuzzing(session)

        model.addAttribute("ref", ref)
        model.addAttribute("question", question)
        model.addAttribute("penalty", question.points / 2)
        model.addAttribute("rows", session.getScoringRows(ref))
        return "score"
    }

    @PostMapping("/board/{board}/category/{category}/question/{question}/score")
    fun toggleResult(
        @HostSession session: QuizSession,
        ref: QuestionRef,
        @RequestParam playerId: Int,
        @RequestParam outcome: Outcome,
    ): String {
        val question = questionService.getQuestion(session, ref)
        try {
            sessions.toggleResult(session, ref, playerId, outcome, question.points)
        } catch (_: QuizSessionException) {
        }
        return "redirect:${ref.toPath("/score")}"
    }
}
