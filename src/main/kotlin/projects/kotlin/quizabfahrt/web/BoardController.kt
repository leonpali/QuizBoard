package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.QuestionRef

@Controller
class BoardController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{board}")
    fun board(
        model: Model,
        @PathVariable board: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val categories = questionService.getBoard(session, QuestionRef.getBoardIndex(board)).categories
        sessions.closeBuzzing(session)

        val isLastBoard = board >= session.boards.size
        model.addAttribute("board", board)
        model.addAttribute("categories", categories)
        model.addAttribute("rows", CompilationEngine.QUESTIONS_PER_CATEGORY)
        model.addAttribute("nextUrl", if (isLastBoard) "/host/scoreboard" else "/board/${board + 1}")
        model.addAttribute("nextLabel", if (isLastBoard) "Zum Endstand" else "Nächstes Board")
        return "board"
    }

    @PostMapping("/board/{board}/category/{category}/question/{question}/open")
    fun openQuestion(
        @PathVariable board: Int,
        @PathVariable category: Int,
        @PathVariable question: Int,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): String {
        val session = sessions.requireHost(hostToken)
        val ref = QuestionRef.fromPath(board, category, question)
        questionService.changeAskedStatus(session, ref)
        sessions.openQuestion(session, ref)
        return "redirect:${ref.toPath()}"
    }
}
