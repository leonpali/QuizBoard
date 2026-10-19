package projects.kotlin.quizabfahrt.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import projects.kotlin.quizabfahrt.application.service.QuizService
import projects.kotlin.quizabfahrt.application.service.QuizStore

@Controller
class HomeController(private val quizService: QuizService, private val quizStore: QuizStore) {

    @GetMapping("/")
    fun home(model: Model): String {
        model.addAttribute("boardCount", quizStore.boards.size)
        model.addAttribute("bundledImages", quizService.bundledImages)
        return "index"
    }

    @PostMapping("/quiz")
    fun createQuiz(
        model: Model,
        @RequestParam("boards", required = false) boards: List<MultipartFile>?,
        @RequestParam("images", required = false) images: List<MultipartFile>?,
    ): String {
        val errors = quizService.createQuiz(boards.orEmpty(), images.orEmpty())
        if (errors.isEmpty()) return "redirect:/board/1"

        model.addAttribute("errors", errors)
        return home(model)
    }
}
