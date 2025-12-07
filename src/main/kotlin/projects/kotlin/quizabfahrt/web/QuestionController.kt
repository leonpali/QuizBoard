package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import projects.kotlin.quizabfahrt.application.service.QuestionService

@Controller
class QuestionController(private val questionService: QuestionService) {

    @GetMapping("/board/{id}/question/{questionId}")
    fun question(model: Model, @PathVariable id: Int, @PathVariable questionId: Int): String {
        val question = questionService.getQuestion(id, questionId)
        model.addAttribute("question", question)
        return "textQuestion"
    }
}