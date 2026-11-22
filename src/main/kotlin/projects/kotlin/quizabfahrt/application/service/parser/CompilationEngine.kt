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
        private val SECTION_HEADER = Regex("(category|question)(_\\d+)?:|answer:")
        private val CONTENT_KEYS = ContentField.entries.map { it.key }
        private val QUESTION_KEYS = setOf("type", "points") + CONTENT_KEYS
        private val ANSWER_KEYS = setOf("type") + CONTENT_KEYS
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
        val questionLine = currentLine
        advanceToken()
        val questionFields = readFields(QUESTION_KEYS)

        if (currentToken != "answer:") error("'answer:' erwartet, gefunden: '$currentToken'")
        val answerLine = currentLine
        advanceToken()
        val answerFields = readFields(ANSWER_KEYS)

        val pointsField = questionFields["points"] ?: errorAt(questionLine, "'points:' fehlt")
        val points = pointsField.value?.toIntOrNull() ?: errorAt(pointsField.line, "'points:' muss eine Zahl sein")

        return Question(
            points,
            compileContent<EQuestionTypes>(questionFields, questionLine, "Frage"),
            compileContent<EAnswerTypes>(answerFields, answerLine, "Antwort"),
        )
    }

    private fun readFields(allowedKeys: Set<String>): Map<String, Field> {
        val fields = LinkedHashMap<String, Field>()
        while (currentToken != "__EOF__" && !SECTION_HEADER.matches(currentToken)) {
            val key = currentToken.substringBefore(":", missingDelimiterValue = "")
            if (key !in allowedKeys) error("Unbekanntes Feld '$currentToken' (erlaubt: ${allowedKeys.joinToString()})")
            if (key in fields) error("'$key:' kommt doppelt vor")
            fields[key] = Field(value(key), currentLine)
            advanceToken()
        }
        return fields
    }

    private inline fun <reified T> compileContent(fields: Map<String, Field>, headerLine: Int, kind: String): Content<T>
            where T : Enum<T>, T : ContentType {
        val typeField = fields["type"] ?: errorAt(headerLine, "$kind: 'type:' fehlt")
        val type = contentTypeOf<T>(typeField.value)
            ?: errorAt(typeField.line, "Unbekannter ${kind}typ '${typeField.value}' (erlaubt: ${enumValues<T>().joinToString { it.key }})")
        return try {
            Content.of(type, ContentField.entries.associateWith { fields[it.key]?.value })
        } catch (e: ContentException) {
            errorAt(headerLine, "$kind: ${e.message}")
        }
    }

    private fun errorAt(line: Int, message: String): Nothing =
        throw QuizParseException("$currentFileName, Zeile $line: $message")

    private data class Field(val value: String?, val line: Int)
}
