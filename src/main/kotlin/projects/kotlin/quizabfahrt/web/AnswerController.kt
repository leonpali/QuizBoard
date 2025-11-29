package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AnswerController {

    @GetMapping("/board/question/answer")
    fun answer(model: Model): String {
        return "textAnswer"
    }
}