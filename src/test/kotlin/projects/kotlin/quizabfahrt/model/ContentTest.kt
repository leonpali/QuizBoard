package projects.kotlin.quizabfahrt.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ContentTest {

    @Test
    fun `only filled in values are kept, trimmed and in field order`() {
        val content = Content.of(EAnswerTypes.TEXTIMAGE, mapOf(ContentField.IMAGE to " dom.jpg ", ContentField.TEXT to "Kölner Dom"))

        assertEquals(listOf(ContentField.TEXT, ContentField.IMAGE), content.values.keys.toList())
        assertEquals("dom.jpg", content.getValue("img"))
        assertNull(Content.of(EQuestionTypes.TEXT, mapOf(ContentField.TEXT to "Frage", ContentField.IMAGE to "  ")).getValue("img"))
    }

    @Test
    fun `every field the type needs must be filled in`() {
        val e = assertFailsWith<ContentException> { Content.of(EAnswerTypes.TEXTIMAGE, mapOf(ContentField.TEXT to " ")) }
        assertEquals("Für den Typ „Bild und Text“ fehlt: Text, Bild", e.message)
    }

    @Test
    fun `files and summary are derived from the fields`() {
        val content = Content.of(EAnswerTypes.TEXTIMAGE, mapOf(ContentField.TEXT to "Kölner Dom", ContentField.IMAGE to "dom.jpg"))

        assertEquals(listOf("dom.jpg"), content.getFiles())
        assertEquals("Kölner Dom · dom.jpg", content.getSummary())
    }

    @Test
    fun `types are found by their key`() {
        assertEquals(EQuestionTypes.LINEUP, contentTypeOf<EQuestionTypes>("lineup"))
        assertEquals(EAnswerTypes.TEXTIMAGE, contentTypeOf<EAnswerTypes>("textimage"))
        assertNull(contentTypeOf<EQuestionTypes>("textimage"))
    }
}
