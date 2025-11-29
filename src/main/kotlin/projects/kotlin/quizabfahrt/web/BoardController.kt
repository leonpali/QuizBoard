package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import projects.kotlin.quizabfahrt.model.Category

@Controller
class BoardController {

    @GetMapping("/board")
    fun board(model: Model):String {
        val categories = listOf(
            Category("History"),
            Category("Biology"),
            Category("Maths"),
            Category("English"),
            Category("Science"),
            Category("Geography"),
        )

        model.addAttribute("categories", categories)
        return "board"
    }
}