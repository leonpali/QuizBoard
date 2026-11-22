package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.QuestionRef

data class BoardCell(val points: Int, val asked: Boolean, val openPath: String)

@Controller
class BoardController(private val questionService: QuestionService, private val sessions: QuizSessionService) {

    @GetMapping("/board/{board}")
    fun board(model: Model, @PathVariable board: Int, @HostSession session: QuizSession): String {
        val boardIndex = QuestionRef.getBoardIndex(board)
        val categories = questionService.getBoard(session, boardIndex).categories
        sessions.closeInput(session)

        val rows = (0 until CompilationEngine.QUESTIONS_PER_CATEGORY).map { row ->
            categories.mapIndexed { category, it ->
                val ref = QuestionRef(boardIndex, category, row)
                BoardCell(it.questions[row].points, session.isAsked(ref), ref.toPath("/open"))
            }
        }

        val isLastBoard = board >= session.boards.size
        model.addAttribute("categories", categories)
        model.addAttribute("rows", rows)
        model.addAttribute("nextUrl", if (isLastBoard) "/host/scoreboard" else "/board/${board + 1}")
        model.addAttribute("nextLabel", if (isLastBoard) "Zum Endstand" else "Nächstes Board")
        return "board"
    }

    @PostMapping("/board/{board}/category/{category}/question/{question}/open")
    fun openQuestion(@HostSession session: QuizSession, ref: QuestionRef): String {
        sessions.openQuestion(session, ref, questionService.getQuestion(session, ref).input)
        return "redirect:${ref.toPath()}"
    }
}
