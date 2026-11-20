package projects.kotlin.quizabfahrt.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class QuestionRefTest {

    @Test
    fun `urls count from 1, indices from 0`() {
        val ref = QuestionRef.fromPath(board = 2, category = 3, question = 1)

        assertEquals(QuestionRef(1, 2, 0), ref)
        assertEquals("/board/2/category/3/question/1", ref.toPath())
        assertEquals("/board/2/category/3/question/1/score", ref.toPath("/score"))
        assertEquals("/board/2", ref.getBoardPath())
        assertEquals(0, QuestionRef.getBoardIndex(1))
    }

    @Test
    fun `refs to the same question are equal, so they work as map keys`() {
        val results = mapOf(QuestionRef(0, 1, 2) to "x")
        assertEquals("x", results[QuestionRef.fromPath(1, 2, 3)])
    }
}
