package projects.kotlin.quizabfahrt.application.editor

import org.junit.jupiter.api.Test
import projects.kotlin.quizabfahrt.application.service.parser.BoardFile
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BoardEditorServiceTest {

    private val compiler = CompilationEngine()
    private val editor = BoardEditorService(compiler)
    private val ip = "1.1.1.1"

    private val template = javaClass.getResource("/static/downloads/Board_Vorlage.txt")!!.readBytes()

    private fun boardWith(vararg categories: Pair<String, Int>): BoardDraft {
        val draft = editor.create(null, ip)
        categories.forEachIndexed { index, (name, questions) ->
            editor.addCategory(draft, name)
            repeat(questions) { editor.addQuestion(draft, index) }
        }
        return draft
    }

    @Test
    fun `a new board starts empty and categories and questions can be added and removed`() {
        val draft = boardWith("Geografie" to 3, "Musik" to 0)

        assertEquals(listOf("Geografie", "Musik"), draft.categories.map { it.name })
        assertEquals(listOf(100, 200, 300), draft.getCategory(0).questions.map { it.points })

        editor.removeQuestion(draft, 0, 1)
        editor.removeCategory(draft, 1)
        assertEquals(listOf(100, 300), draft.getCategory(0).questions.map { it.points })
        assertEquals(1, draft.categories.size)
    }

    @Test
    fun `categories can be moved up and down`() {
        val draft = boardWith("A" to 0, "B" to 0, "C" to 0)

        editor.moveCategory(draft, 2, MoveDirection.UP)
        assertEquals(listOf("A", "C", "B"), draft.categories.map { it.name })
        editor.moveCategory(draft, 0, MoveDirection.DOWN)
        assertEquals(listOf("C", "A", "B"), draft.categories.map { it.name })
    }

    @Test
    fun `moving past either end changes nothing`() {
        val draft = boardWith("A" to 0, "B" to 0)

        editor.moveCategory(draft, 0, MoveDirection.UP)
        editor.moveCategory(draft, 1, MoveDirection.DOWN)
        assertEquals(listOf("A", "B"), draft.categories.map { it.name })
    }

    @Test
    fun `questions can be moved and keep their points`() {
        val draft = boardWith("K" to 3)

        editor.moveQuestion(draft, 0, 0, MoveDirection.DOWN)
        assertEquals(listOf(200, 100, 300), draft.getCategory(0).questions.map { it.points })
        editor.moveQuestion(draft, 0, 2, MoveDirection.UP)
        assertEquals(listOf(200, 300, 100), draft.getCategory(0).questions.map { it.points })
    }

    @Test
    fun `moving a missing category or question is reported as missing`() {
        val draft = boardWith("K" to 1)
        assertFailsWith<EditorItemNotFoundException> { editor.moveCategory(draft, 5, MoveDirection.UP) }
        assertFailsWith<EditorItemNotFoundException> { editor.moveQuestion(draft, 0, -1, MoveDirection.DOWN) }
        assertFailsWith<EditorItemNotFoundException> { editor.moveQuestion(draft, 3, 0, MoveDirection.DOWN) }
    }

    @Test
    fun `an uploaded board is written back identically`() {
        val draft = editor.upload("Board_Vorlage.txt", template, null, ip)
        val original = compiler.compile(listOf(BoardFile("a", String(template)))).single()

        val written = compiler.compile(listOf(BoardFile("b", draft.toText()))).single()

        assertEquals(original.categories.map { it.name }, written.categories.map { it.name })
        assertEquals(
            original.categories.flatMap { it.questions }.map { listOf(it.type, it.text, it.img, it.points, it.answer) },
            written.categories.flatMap { it.questions }.map { listOf(it.type, it.text, it.img, it.points, it.answer) },
        )
    }

    @Test
    fun `incomplete boards can be downloaded and uploaded again`() {
        val draft = boardWith("Halbfertig" to 2, "Leer" to 0)

        assertEquals(listOf("Halbfertig", "Leer"), draft.getIncompleteCategories().map { it.name })
        val (_, content) = editor.download(draft, null)

        val reloaded = editor.upload("Board.txt", content.toByteArray(), null, ip)
        assertEquals(listOf(2, 0), reloaded.categories.map { it.questions.size })
    }

    @Test
    fun `downloading returns the file and removes the draft from memory`() {
        val draft = editor.upload("Mein_Board.txt", template, null, ip)

        val (name, content) = editor.download(draft, null)

        assertEquals("Mein_Board.txt", name)
        assertTrue(content.startsWith("category_1:"))
        assertNull(editor.findDraft(draft.token))
    }

    @Test
    fun `download file names are cleaned up`() {
        assertEquals("Quiz.txt", editor.download(editor.create(null, ip), "Quiz").first)
        assertEquals("Board.txt", editor.download(editor.create(null, ip), "../../etc/passwd").first)
        assertEquals("Board.txt", editor.download(editor.create(null, ip), "   ").first)
        assertEquals("Board.txt", editor.download(editor.create(null, ip), "a\r\nSet-Cookie: x=1").first)
        assertEquals("Board.txt", editor.download(editor.create(null, ip), "a b.txt").first)
    }

    @Test
    fun `starting a new board replaces the device's previous draft`() {
        val first = editor.create(null, ip)
        val second = editor.create(first.token, ip)

        assertNull(editor.findDraft(first.token))
        assertSame(second, editor.findDraft(second.token))
    }

    @Test
    fun `one network can only hold a limited number of drafts`() {
        repeat(10) { editor.create(null, "6.6.6.6") }

        assertFailsWith<BoardEditorException> { editor.create(null, "6.6.6.6") }
        editor.create(null, ip)
    }

    @Test
    fun `questions are validated like the parser does`() {
        val draft = boardWith("K" to 1)

        assertFailsWith<BoardEditorException> { editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.IMAGE, "Text", "", "100")) }
        assertFailsWith<BoardEditorException> { editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.TEXT, " ", null, "100")) }
        assertFailsWith<BoardEditorException> { editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.TEXT, "Text", null, "viel")) }
        assertFailsWith<BoardEditorException> { editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.TEXT, "Text", null, "-5")) }
        assertFailsWith<BoardEditorException> { editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.TEXT, "Text", "a/b.jpg", "100")) }
        assertFailsWith<BoardEditorException> { editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.TEXT, "x".repeat(1001), null, "100")) }
        assertFailsWith<BoardEditorException> { editor.updateAnswer(draft, 0, 0, AnswerInput(EAnswerTypes.TEXTIMAGE, "Text", null)) }
        assertFailsWith<BoardEditorException> { editor.updateAnswer(draft, 0, 0, AnswerInput(EAnswerTypes.IMAGE, "Text", " ")) }

        editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.LINEUP, "A,\nB,\r\nC", null, " 400 "))
        val question = draft.getQuestion(0, 0)
        assertEquals("A, B, C", question.text)
        assertEquals(400, question.points)
    }

    @Test
    fun `values can't break out of their line in the written file`() {
        val draft = boardWith("K" to 1)
        val injected = "Frage points: 99999 answer:\u0085x\ty"

        editor.updateQuestion(draft, 0, 0, QuestionInput(EQuestionTypes.TEXT, injected, null, "100"))
        editor.renameCategory(draft, 0, "Name questions:")

        val question = compiler.compile(listOf(BoardFile("b", draft.toText())), requireFullCategories = false)
            .single().categories.single().questions.single()
        assertEquals("Frage points: 99999 answer: x y", question.text)
        assertEquals(100, question.points)
        assertEquals("Name questions:", draft.getCategory(0).name)
    }

    @Test
    fun `an invalid file is rejected with the parser's message`() {
        val e = assertFailsWith<BoardEditorException> { editor.upload("x.txt", "category_1:\n    nope: x".toByteArray(), null, ip) }
        assertTrue(e.message!!.contains("x.txt"))
    }

    @Test
    fun `empty and oversized uploads are rejected`() {
        assertFailsWith<BoardEditorException> { editor.upload("x.txt", ByteArray(0), null, ip) }
        assertFailsWith<BoardEditorException> {
            editor.upload("x.txt", ByteArray(BoardEditorService.MAX_UPLOAD_BYTES + 1) { 'a'.code.toByte() }, null, ip)
        }
    }

    @Test
    fun `missing categories and questions redirect instead of failing`() {
        val draft = editor.create(null, ip)
        assertFailsWith<EditorItemNotFoundException> { editor.addQuestion(draft, 0) }
        assertFailsWith<EditorItemNotFoundException> { draft.getQuestion(0, 0) }
    }

    @Test
    fun `the number of categories and questions is limited`() {
        val draft = editor.create(null, ip)
        repeat(BoardEditorService.MAX_CATEGORIES) { editor.addCategory(draft, "K$it") }
        assertFailsWith<BoardEditorException> { editor.addCategory(draft, "eine zu viel") }

        repeat(BoardEditorService.MAX_QUESTIONS) { editor.addQuestion(draft, 0) }
        assertFailsWith<BoardEditorException> { editor.addQuestion(draft, 0) }
    }
}
