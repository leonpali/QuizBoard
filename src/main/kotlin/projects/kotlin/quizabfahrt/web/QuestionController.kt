package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class QuestionController {

    @GetMapping("/board/{id}/question/{questionId}")
    fun question(model: Model, @PathVariable id: String, @PathVariable questionId: String): String {
        return "textQuestion"
    }
}