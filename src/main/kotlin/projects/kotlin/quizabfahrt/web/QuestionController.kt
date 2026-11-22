package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.QuestionRef

@Controller
class QuestionController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{board}/category/{category}/question/{question}")
    fun question(model: Model, @HostSession session: QuizSession, ref: QuestionRef): String {
        model.addAttribute("question", questionService.getQuestion(session, ref))
        model.addAttribute("ref", ref)
        return "question"
    }

    @GetMapping("/board/{board}/category/{category}/question/{question}/answer")
    fun answer(model: Model, @HostSession session: QuizSession, ref: QuestionRef): String {
        model.addAttribute("question", questionService.getQuestion(session, ref))
        sessions.closeInput(session)
        model.addAttribute("nextUrl", if (session.hasPlayers()) ref.toPath("/score") else ref.getBoardPath())
        return "answer"
    }
}
