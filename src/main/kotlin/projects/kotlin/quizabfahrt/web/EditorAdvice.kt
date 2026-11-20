package projects.kotlin.quizabfahrt.web

import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import projects.kotlin.quizabfahrt.application.editor.EditorItemNotFoundException
import projects.kotlin.quizabfahrt.application.editor.NoDraftException

@ControllerAdvice
class EditorAdvice {

    @ExceptionHandler(NoDraftException::class)
    fun noDraft(): String = "redirect:/editor"

    @ExceptionHandler(EditorItemNotFoundException::class)
    fun itemGone(): String = "redirect:/editor/board"
}
