package projects.kotlin.quizabfahrt.application.service.parser

import org.junit.jupiter.api.Test
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EInputTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CompilationEngineTest {

    private val compiler = CompilationEngine()

    @Test
    fun `the downloadable board template is valid`() {
        val content = javaClass.getResource("/static/downloads/Board_Vorlage.txt")!!.readText()

        val board = compiler.compile(listOf(BoardFile("Board_Vorlage.txt", content))).single()

        val questions = board.categories.single().questions
        assertEquals(6, questions.size)
        assertEquals(
            listOf(EQuestionTypes.TEXT, EQuestionTypes.IMAGE, EQuestionTypes.TEXT, EQuestionTypes.IMAGE, EQuestionTypes.LINEUP, EQuestionTypes.TEXT),
            questions.map { it.content.type },
        )
        assertEquals(
            listOf(EAnswerTypes.TEXT, EAnswerTypes.TEXT, EAnswerTypes.IMAGE, EAnswerTypes.TEXTIMAGE, EAnswerTypes.TEXT, EAnswerTypes.TEXT),
            questions.map { it.answer.type },
        )
        assertEquals(EInputTypes.TEXT, questions.first().input)
    }

    @Test
    fun `lines starting with a hash are ignored`() {
        val questions = (1..6).joinToString("\n") {
            """
            question_$it:
                # a comment between fields
                type: text
                text: Frage $it
                img: null
                points: ${it * 100}
                answer:
                    type: text
                    text: Antwort $it
                    img: null
            """.trimIndent()
        }
        val content = "# header\ncategory_1:\n    name: K\n    questions:\n$questions\n# footer"

        val board = compiler.compile(listOf(BoardFile("Board_1.txt", content))).single()

        assertEquals(listOf(100, 200, 300, 400, 500, 600), board.categories.single().questions.map { it.points })
    }

    private fun board(vararg questions: String) =
        "category_1:\n    name: K\n    questions:\n" + questions.mapIndexed { i, q -> "question_${i + 1}:\n$q" }.joinToString("\n")

    private fun parseLenient(content: String) =
        compiler.compile(listOf(BoardFile("Board_1.txt", content)), requireFullCategories = false).single()

    private fun parseError(content: String) =
        assertFailsWith<QuizParseException> { parseLenient(content) }.message!!

    @Test
    fun `fields can come in any order and unneeded ones can be left out`() {
        val question = parseLenient(board("""
            points: 300
            img: koeln.jpg
            type: img
            answer:
                text: Kölner Dom
                type: text
        """.trimIndent())).categories.single().questions.single()

        assertEquals(300, question.points)
        assertEquals("koeln.jpg", question.getValue("img"))
        assertNull(question.getValue("text"))
        assertEquals("Kölner Dom", question.getAnswerValue("text"))
    }

    @Test
    fun `unknown and repeated fields are rejected with their line`() {
        assertContains(
            parseError(board("type: text\ntext: Frage\nunbekannt: x\npoints: 100\nanswer:\ntype: text\ntext: A")),
            "Board_1.txt, Zeile 7: Unbekanntes Feld 'unbekannt: x' (erlaubt: type, points, input, text, img",
        )
        assertContains(parseError(board("type: text\ntext: Frage\ntext: Nochmal\npoints: 100\nanswer:\ntype: text\ntext: A")), "'text:' kommt doppelt vor")
    }

    @Test
    fun `missing type, points and required fields are reported at the question`() {
        assertEquals("Board_1.txt, Zeile 4: Frage: 'type:' fehlt", parseError(board("text: Frage\npoints: 100\nanswer:\ntype: text\ntext: A")))
        assertEquals("Board_1.txt, Zeile 4: 'points:' fehlt", parseError(board("type: text\ntext: Frage\nanswer:\ntype: text\ntext: A")))
        assertEquals(
            "Board_1.txt, Zeile 4: Frage: Für den Typ „Bild“ fehlt: Bild",
            parseError(board("type: img\ntext: Frage\npoints: 100\nanswer:\ntype: text\ntext: A")),
        )
        assertEquals(
            "Board_1.txt, Zeile 8: Antwort: Für den Typ „Bild und Text“ fehlt: Bild",
            parseError(board("type: text\ntext: Frage\npoints: 100\nanswer:\ntype: textimage\ntext: A")),
        )
        assertContains(parseError(board("type: unbekannt\npoints: 100\nanswer:\ntype: text\ntext: A")), "Unbekannter Fragetyp 'unbekannt' (erlaubt: text, img, lineup")
    }

    @Test
    fun `the input type is optional and defaults to the buzzer`() {
        val questions = parseLenient(board(
            "type: text\ntext: F\npoints: 100\nanswer:\ntype: text\ntext: A",
            "type: text\ntext: F\npoints: 100\ninput: text\nanswer:\ntype: text\ntext: A",
            "type: text\ntext: F\npoints: 100\ninput: null\nanswer:\ntype: text\ntext: A",
        )).categories.single().questions

        assertEquals(listOf(EInputTypes.BUZZER, EInputTypes.TEXT, EInputTypes.BUZZER), questions.map { it.input })
        assertContains(
            parseError(board("type: text\ntext: F\npoints: 100\ninput: gesten\nanswer:\ntype: text\ntext: A")),
            "Zeile 8: Unbekannte Eingabe 'gesten' (erlaubt: buzzer, text",
        )
    }

    @Test
    fun `a question without answer section is rejected`() {
        assertContains(parseError(board("type: text\ntext: Frage\npoints: 100") + "\nquestion_2:"), "'answer:' erwartet, gefunden: 'question_2:'")
    }

    @Test
    fun `written boards contain only the filled in fields and read back identically`() {
        val template = javaClass.getResource("/static/downloads/Board_Vorlage.txt")!!.readText()
        val original = compiler.compile(listOf(BoardFile("a", template))).single()

        val written = BoardWriter.write(original.categories)

        assertEquals(original, compiler.compile(listOf(BoardFile("b", written))).single())
        assertFalse("null" in written)
        assertContains(written, "input: text")
    }
}
