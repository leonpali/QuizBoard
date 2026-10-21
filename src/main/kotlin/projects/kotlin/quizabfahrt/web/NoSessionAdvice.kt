package projects.kotlin.quizabfahrt.web

import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import projects.kotlin.quizabfahrt.application.session.NoSessionException
import java.io.IOException

@ControllerAdvice
class NoSessionAdvice {

    @ExceptionHandler(NoSessionException::class)
    fun noSession(): String = "redirect:/"

    @ExceptionHandler(IOException::class)
    fun clientGone() {
    }
}
