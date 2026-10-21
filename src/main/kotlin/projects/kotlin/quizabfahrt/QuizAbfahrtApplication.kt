package projects.kotlin.quizabfahrt

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class QuizAbfahrtApplication

fun main(args: Array<String>) {
    runApplication<QuizAbfahrtApplication>(*args)
}
