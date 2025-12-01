package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import projects.kotlin.quizabfahrt.model.Category
import projects.kotlin.quizabfahrt.parser.CompilationEngine
import projects.kotlin.quizabfahrt.parser.Parser

@Controller
class BoardController() {

    @GetMapping("/board")
    fun board(model: Model): String {

        val categories = CompilationEngine.getBoard(0).categories

        model.addAttribute("categories", categories)
        return "board"
    }
}