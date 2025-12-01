package projects.kotlin.quizabfahrt.parser

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Service

@Service
class Parser(private val compiler: CompilationEngine): CommandLineRunner {

    override fun run(vararg args: String?) {
        parse()
    }

    fun parse() {
        compiler.compile()
    }
}