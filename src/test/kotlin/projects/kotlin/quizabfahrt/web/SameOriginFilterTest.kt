package projects.kotlin.quizabfahrt.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

class SameOriginFilterTest {

    private val filter = SameOriginFilter()

    private fun statusFor(method: String, vararg headers: Pair<String, String>): Int {
        val request = MockHttpServletRequest(method, "/host/end").apply {
            scheme = "https"
            serverName = "quiz.example.de"
            serverPort = 443
            headers.forEach { (name, value) -> addHeader(name, value) }
        }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return response.status
    }

    @Test
    fun `posts from the quiz itself are allowed`() {
        assertEquals(200, statusFor("POST", "Sec-Fetch-Site" to "same-origin", "Origin" to "https://quiz.example.de"))
        assertEquals(200, statusFor("POST", "Origin" to "https://quiz.example.de"))
    }

    @Test
    fun `posts from other sites and other subdomains are rejected`() {
        assertEquals(403, statusFor("POST", "Sec-Fetch-Site" to "cross-site", "Origin" to "https://evil.example"))
        assertEquals(403, statusFor("POST", "Sec-Fetch-Site" to "same-site", "Origin" to "https://notes.example.de"))
        assertEquals(403, statusFor("POST", "Origin" to "https://notes.example.de"))
        assertEquals(403, statusFor("POST", "Origin" to "null"))
    }

    @Test
    fun `requests without browser headers and safe methods pass`() {
        assertEquals(200, statusFor("POST"))
        assertEquals(200, statusFor("POST", "Sec-Fetch-Site" to "none"))
        assertEquals(200, statusFor("GET", "Sec-Fetch-Site" to "cross-site", "Origin" to "https://evil.example"))
    }
}
