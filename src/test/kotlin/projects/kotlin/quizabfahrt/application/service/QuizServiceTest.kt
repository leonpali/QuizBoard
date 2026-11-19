package projects.kotlin.quizabfahrt.application.service

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizServiceTest {

    @Test
    fun `common raster images are allowed, regardless of case`() {
        listOf("a.jpg", "b.JPEG", "c.png", "d.gif", "e.webp", "f.avif", "g.bmp", "köln.Jpg").forEach {
            assertTrue(QuizService.isAllowedImage(it), it)
        }
    }

    @Test
    fun `scriptable and unknown formats are rejected`() {
        listOf("evil.svg", "page.html", "page.htm", "x.js", "noextension", "image.png.svg").forEach {
            assertFalse(QuizService.isAllowedImage(it), it)
        }
    }
}
