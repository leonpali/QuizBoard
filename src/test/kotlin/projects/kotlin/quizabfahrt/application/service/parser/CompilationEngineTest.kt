package projects.kotlin.quizabfahrt.application.service.parser

import org.junit.jupiter.api.Test
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import kotlin.test.assertEquals

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
            questions.map { it.type },
        )
        assertEquals(
            listOf(EAnswerTypes.TEXT, EAnswerTypes.TEXT, EAnswerTypes.IMAGE, EAnswerTypes.TEXTIMAGE, EAnswerTypes.TEXT, EAnswerTypes.TEXT),
            questions.map { it.answer.type },
        )
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
}
