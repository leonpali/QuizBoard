package projects.kotlin.quizabfahrt.parser

import org.springframework.stereotype.Service
import java.util.LinkedList

@Service
class Parser {

    private val tokenizer = Tokenizer()

    fun parse() {
        val allTokens = tokenizer.tokenize()
    }
}