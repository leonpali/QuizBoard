package projects.kotlin.quizabfahrt.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityHeadersFilterTest {

    private val filter = SecurityHeadersFilter()

    private fun run(path: String, secure: Boolean = false): Pair<MockHttpServletRequest, MockHttpServletResponse> {
        val request = MockHttpServletRequest("GET", path).apply { isSecure = secure }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return request to response
    }

    @Test
    fun `every response gets the basic headers`() {
        val (_, response) = run("/")
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
        assertEquals("DENY", response.getHeader("X-Frame-Options"))
        assertEquals("same-origin", response.getHeader("Referrer-Policy"))
    }

    @Test
    fun `pages only allow scripts with this request's nonce`() {
        val (request, response) = run("/host")
        val nonce = request.getAttribute(SecurityHeadersFilter.NONCE_ATTRIBUTE) as String
        val policy = response.getHeader("Content-Security-Policy")!!

        assertTrue("script-src 'nonce-$nonce'" in policy)
        assertTrue("frame-ancestors 'none'" in policy)
        assertNotEquals(nonce, run("/host").first.getAttribute(SecurityHeadersFilter.NONCE_ATTRIBUTE))
    }

    @Test
    fun `images get a policy that never runs scripts`() {
        val (request, response) = run("/images/x.svg")
        assertEquals("default-src 'none'; style-src 'unsafe-inline'; sandbox", response.getHeader("Content-Security-Policy"))
        assertNull(request.getAttribute(SecurityHeadersFilter.NONCE_ATTRIBUTE))
    }

    @Test
    fun `HSTS is only sent over HTTPS`() {
        assertNull(run("/").second.getHeader("Strict-Transport-Security"))
        assertEquals("max-age=31536000", run("/", secure = true).second.getHeader("Strict-Transport-Security"))
    }
}
