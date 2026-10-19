package projects.kotlin.quizabfahrt.application.service.parser

import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.model.*
import java.util.LinkedList
import java.util.Scanner

data class BoardFile(val name: String, val content: String)

class QuizParseException(message: String) : RuntimeException(message)

@Service
class CompilationEngine {

    companion object {
        const val QUESTIONS_PER_CATEGORY = 6
    }

    private var sc: Scanner? = null
    private var currentFileName = ""
    private var currentLine = 0
    private var currentToken = ""
    private var currentQuestionId = 0
    private var currentCategoryId = 0

    @Synchronized
    fun compile(files: List<BoardFile>): List<Board> {
        val boards = LinkedList<Board>()
        for (f: BoardFile in files) {
            currentFileName = f.name
            currentLine = 0
            currentQuestionId = 0
            currentCategoryId = 0
            sc = Scanner(f.content.removePrefix("﻿"))
            advanceToken()
            boards.add(compileBoard(boards.size))
            sc!!.close()
        }
        return boards
    }

    private fun advanceToken() {
        currentToken = "__EOF__"
        while (sc!!.hasNextLine()) {
            currentLine++
            val line = sc!!.nextLine().trim()
            if (line.isNotEmpty()) {
                currentToken = line
                return
            }
        }
    }

    private fun error(message: String): Nothing {
        val location = if (currentToken == "__EOF__") "Dateiende" else "Zeile $currentLine"
        throw QuizParseException("$currentFileName, $location: $message")
    }

    private fun value(key: String): String? {
        if (!currentToken.startsWith("$key:")) error("'$key:' erwartet, gefunden: '$currentToken'")
        val v = currentToken.substringAfter(":").trim()
        return if (v == "" || v == "null") null else v
    }

    private fun compileBoard(id: Int): Board {
        val categories = LinkedList<Category>()

        while (currentToken != "__EOF__") {
            categories.add(compileCategory())
        }
        if (categories.isEmpty()) error("Datei enthält keine Kategorien")
        return Board(id, categories)
    }

    private fun compileCategory(): Category {
        val questions = LinkedList<Question>()
        val id = currentCategoryId
        currentCategoryId++

        if (!currentToken.startsWith("category")) error("'category_N:' erwartet, gefunden: '$currentToken'")
        advanceToken()

        val name = value("name") ?: ""
        advanceToken()

        value("questions")
        advanceToken()

        while (!currentToken.startsWith("category") && currentToken != "__EOF__") {
            questions.add(compileQuestion())
        }

        if (questions.size != QUESTIONS_PER_CATEGORY) {
            error("Kategorie '$name' hat ${questions.size} Fragen, erwartet werden $QUESTIONS_PER_CATEGORY")
        }
        return Category(id, name, questions)
    }

    private fun compileQuestion(): Question {
        val id = currentQuestionId

        if (!currentToken.startsWith("question")) error("'question_N:' erwartet, gefunden: '$currentToken'")
        advanceToken()

        val type = when (val typeString = value("type")) {
            "text" -> EQuestionTypes.TEXT
            "img" -> EQuestionTypes.IMAGE
            "lineup" -> EQuestionTypes.LINEUP
            else -> error("Unbekannter Fragetyp '$typeString' (erlaubt: text, img, lineup)")
        }
        advanceToken()

        val text = value("text")
        if (type != EQuestionTypes.IMAGE && text == null) error("Frage vom Typ '${type.name.lowercase()}' braucht einen Text")
        advanceToken()

        val img = value("img")
        if (type == EQuestionTypes.IMAGE && img == null) error("Bildfrage braucht ein Bild")
        advanceToken()

        val points = value("points")?.toIntOrNull() ?: error("'points:' muss eine Zahl sein")
        advanceToken()

        val answer = compileAnswer()

        return Question(id, type, text, img, points, answer)
    }

    private fun compileAnswer(): Answer {
        val id = currentQuestionId
        currentQuestionId++

        if (!currentToken.startsWith("answer")) error("'answer:' erwartet, gefunden: '$currentToken'")
        advanceToken()

        val type = when (val typeString = value("type")) {
            "text" -> EAnswerTypes.TEXT
            "img" -> EAnswerTypes.IMAGE
            "textimage" -> EAnswerTypes.TEXTIMAGE
            else -> error("Unbekannter Antworttyp '$typeString' (erlaubt: text, img, textimage)")
        }
        advanceToken()

        val text = value("text")
        if (type != EAnswerTypes.IMAGE && text == null) error("Antwort vom Typ '${type.name.lowercase()}' braucht einen Text")
        advanceToken()

        val img = value("img")
        if (type != EAnswerTypes.TEXT && img == null) error("Antwort vom Typ '${type.name.lowercase()}' braucht ein Bild")
        advanceToken()

        return Answer(id, type, text, img)
    }
}
