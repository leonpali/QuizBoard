package projects.kotlin.quizabfahrt.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.SecureRandom
import java.util.Base64

@Component
class SecurityHeadersFilter : OncePerRequestFilter() {

    companion object {
        const val NONCE_ATTRIBUTE = "cspNonce"
        private const val IMAGE_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox"
    }

    private val random = SecureRandom()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Referrer-Policy", "same-origin")
        if (request.isSecure) response.setHeader("Strict-Transport-Security", "max-age=31536000")
        response.setHeader(
            "Content-Security-Policy",
            if (request.requestURI.startsWith("/images/")) IMAGE_POLICY else getPagePolicy(request),
        )
        filterChain.doFilter(request, response)
    }

    private fun getPagePolicy(request: HttpServletRequest): String {
        val nonce = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
        request.setAttribute(NONCE_ATTRIBUTE, nonce)
        return "default-src 'self'; script-src 'nonce-$nonce'; object-src 'none'; base-uri 'none'; " +
            "form-action 'self'; frame-ancestors 'none'"
    }
}
