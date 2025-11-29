package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class QuestionController {

    @GetMapping("/board/question")
    fun question(model: Model): String {
        return "textQuestion"
    }
}