package projects.kotlin.quizabfahrt.application.service.parser

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.application.service.QuizService

@Service
class Parser(private val quizService: QuizService): CommandLineRunner {

    private val log = LoggerFactory.getLogger(Parser::class.java)

    override fun run(vararg args: String?) {
        try {
            quizService.loadBundledQuiz()
        } catch (e: QuizParseException) {
            log.warn("Bundled boards could not be loaded: {}", e.message)
        }
    }
}
