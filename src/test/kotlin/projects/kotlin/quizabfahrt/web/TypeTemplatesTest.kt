package projects.kotlin.quizabfahrt.web

import org.junit.jupiter.api.Test
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import kotlin.test.assertContains
import kotlin.test.fail

class TypeTemplatesTest {

    private fun assertHasContentFragment(path: String) {
        val template = javaClass.getResource("/templates/$path") ?: fail("Missing template $path")
        assertContains(template.readText(), "th:fragment=\"content\"", message = "$path needs a fragment named content")
    }

    @Test
    fun `every question type has a fragment`() {
        EQuestionTypes.entries.forEach { assertHasContentFragment("questions/${it.key}.html") }
    }

    @Test
    fun `every answer type has a fragment`() {
        EAnswerTypes.entries.forEach { assertHasContentFragment("answers/${it.key}.html") }
    }
}
