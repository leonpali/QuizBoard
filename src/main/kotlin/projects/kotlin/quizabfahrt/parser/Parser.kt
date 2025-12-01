package projects.kotlin.quizabfahrt.parser

import org.springframework.stereotype.Service

@Service
class Parser {

    private val compiler = CompilationEngine()

    fun parse() {
        compiler.compile()
    }
}