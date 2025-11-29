package projects.kotlin.quizabfahrt.parser

import java.io.File
import java.util.LinkedList
import java.util.Scanner

class Tokenizer {

    private var sc: Scanner? = null
    private val allFiles = LinkedList<File>()
    private val allTokens = LinkedList<Token>()

    init {
        for (f: File in loadFiles()) {
            if (f.name.contains("Board")) allFiles.add(f)
        }
    }

    fun tokenize(): List<Token> {
        for (f: File in allFiles) {
            sc = Scanner(f)
        }
        return emptyList()
    }

    private fun loadFiles(): List<File> {
        val resource = this::class.java.getResource("/boards")
            ?: throw IllegalStateException("board directory not found")

        val dir = File(resource.toURI())

        return dir.listFiles()?.toList() ?: emptyList()
    }
}