package projects.kotlin.quizabfahrt.parser

import projects.kotlin.quizabfahrt.model.*
import java.io.File
import java.util.LinkedList
import java.util.Scanner

class CompilationEngine {

    companion object {
        private val allBoards = LinkedList<Board>()

        fun getBoard(id: Int): Board {
            return allBoards[id]
        }
    }

    private var sc: Scanner? = null
    private val allFiles = LinkedList<File>()
    private var currentToken = ""
    private var currentQuestionId = 0
    private var currentCategoryId = 0
    private var currentBoardId = 0

    init {
        for (f: File in loadFiles()) {
            if (f.name.contains("Board")) allFiles.add(f)
        }
    }

    fun compile() {
        for (f: File in allFiles) {
            sc = Scanner(f)
            allBoards.add(compileBoard())
        }
    }

    private fun advanceToken() {
        currentToken = sc!!.nextLine()
    }

    private fun loadFiles(): List<File> {
        val resource = this::class.java.getResource("/boards")
            ?: throw IllegalStateException("board directory not found")

        val dir = File(resource.toURI())

        return dir.listFiles()?.toList() ?: emptyList()
    }

    private fun compileBoard(): Board {
        val id = currentBoardId
        currentBoardId++
        val categories = LinkedList<Category>()

        while (sc!!.hasNext()) {
            categories.add(compileCategory())
        }
        return Board(id,categories)
    }

    private fun compileCategory(): Category {
        var name = ""
        val questions = LinkedList<Question>()
        val id = currentCategoryId
        currentCategoryId++

        if (currentToken.contains("category")) {
            advanceToken()

            name = currentToken.drop(6)
            advanceToken()

            advanceToken()

            while (!currentToken.contains("category")) {
                questions.add(compileQuestion())
            }
        }
        return Category(id, name, questions)
    }

    private fun compileQuestion(): Question {
        val id = currentQuestionId
        var type = EQuestionTypes.TEXT
        var text: String? = ""
        var img: String? = ""
        var points = 100
        var answer: Answer? = Answer(1,"1","1")

        if (currentToken.contains("question")) {
            advanceToken()

            val typeString = currentToken.drop(6)

            when (typeString) {
                "text" -> type = EQuestionTypes.TEXT
                "image" -> type = EQuestionTypes.IMAGE
            }
            advanceToken()

            text = currentToken.drop(6)
            if (text == "") {
                text = null
            }
            advanceToken()

            img = currentToken.drop(5)
            if (img == "") {
                img = null
            }
            advanceToken()

            points = currentToken.drop(8).toInt()
            advanceToken()

            answer = compileAnswer()
        }

        return Question(id, type, text, img, points, answer!!)
    }

    private fun compileAnswer(): Answer {
        val id = currentQuestionId
        currentQuestionId++

        var text: String? = ""
        var img: String? = ""

        if (currentToken.contains("answer")) {
            advanceToken()

            text = currentToken.drop(6)
            if (text == "") {
                text = null
            }
            advanceToken()

            img = currentToken.drop(5)
            if (img == "") {
                img = null
            }
        }

        advanceToken()

        return Answer(id, text, img)
    }
}