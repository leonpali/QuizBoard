package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import projects.kotlin.quizabfahrt.application.service.QuestionService
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine

@Controller
class BoardController(private val questionService: QuestionService) {

    @GetMapping("/board/{id}")
    fun board(model: Model, @PathVariable id: Int): String {

        val categories = CompilationEngine.getBoard(id-1).categories

        model.addAttribute("categories", categories)
        model.addAttribute("id", id-1)
        return "board"
    }

    @PostMapping("/board/{id}/{questionId}")
    fun chooseQuestion(model: Model, @PathVariable id: Int, @PathVariable questionId: Int): String {
        questionService.changeAskedStatus(id,questionId)
        return "redirect:/board/${id+1}/question/$questionId"
    }
}