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
    private var requireFullCategories = true

    @Synchronized
    fun compile(files: List<BoardFile>, requireFullCategories: Boolean = true): List<Board> {
        this.requireFullCategories = requireFullCategories
        val boards = LinkedList<Board>()
        for (f: BoardFile in files) {
            currentFileName = f.name
            currentLine = 0
            sc = Scanner(f.content.removePrefix("﻿"))
            advanceToken()
            boards.add(compileBoard())
            sc!!.close()
        }
        return boards
    }

    private fun advanceToken() {
        currentToken = "__EOF__"
        while (sc!!.hasNextLine()) {
            currentLine++
            val line = sc!!.nextLine().trim()
            if (line.isNotEmpty() && !line.startsWith("#")) {
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

    private fun compileBoard(): Board {
        val categories = LinkedList<Category>()

        while (currentToken != "__EOF__") {
            categories.add(compileCategory())
        }
        if (categories.isEmpty() && requireFullCategories) error("Datei enthält keine Kategorien")
        return Board(categories)
    }

    private fun compileCategory(): Category {
        val questions = LinkedList<Question>()

        if (!currentToken.startsWith("category")) error("'category_N:' erwartet, gefunden: '$currentToken'")
        advanceToken()

        val name = value("name") ?: ""
        advanceToken()

        value("questions")
        advanceToken()

        while (!currentToken.startsWith("category") && currentToken != "__EOF__") {
            questions.add(compileQuestion())
        }

        if (requireFullCategories && questions.size != QUESTIONS_PER_CATEGORY) {
            error("Kategorie '$name' hat ${questions.size} Fragen, erwartet werden $QUESTIONS_PER_CATEGORY")
        }
        return Category(name, questions)
    }

    private fun compileQuestion(): Question {
        if (!currentToken.startsWith("question")) error("'question_N:' erwartet, gefunden: '$currentToken'")
        advanceToken()

        val typeString = value("type")
        val type = EQuestionTypes.entries.firstOrNull { it.key == typeString }
            ?: error("Unbekannter Fragetyp '$typeString' (erlaubt: ${EQuestionTypes.entries.joinToString { it.key }})")
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

        return Question(type, text, img, points, answer)
    }

    private fun compileAnswer(): Answer {
        if (!currentToken.startsWith("answer")) error("'answer:' erwartet, gefunden: '$currentToken'")
        advanceToken()

        val typeString = value("type")
        val type = EAnswerTypes.entries.firstOrNull { it.key == typeString }
            ?: error("Unbekannter Antworttyp '$typeString' (erlaubt: ${EAnswerTypes.entries.joinToString { it.key }})")
        advanceToken()

        val text = value("text")
        if (type != EAnswerTypes.IMAGE && text == null) error("Antwort vom Typ '${type.name.lowercase()}' braucht einen Text")
        advanceToken()

        val img = value("img")
        if (type != EAnswerTypes.TEXT && img == null) error("Antwort vom Typ '${type.name.lowercase()}' braucht ein Bild")
        advanceToken()

        return Answer(type, text, img)
    }
}
