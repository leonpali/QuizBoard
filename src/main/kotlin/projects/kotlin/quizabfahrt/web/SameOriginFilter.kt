package projects.kotlin.quizabfahrt.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class SameOriginFilter : OncePerRequestFilter() {

    companion object {
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.method in SAFE_METHODS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (isCrossOrigin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isCrossOrigin(request: HttpServletRequest): Boolean {
        val fetchSite = request.getHeader("Sec-Fetch-Site")
        if (fetchSite != null) return fetchSite != "same-origin" && fetchSite != "none"
        val origin = request.getHeader("Origin") ?: return false
        return origin != getOwnOrigin(request)
    }

    private fun getOwnOrigin(request: HttpServletRequest): String {
        val defaultPort = (request.scheme == "https" && request.serverPort == 443) ||
            (request.scheme == "http" && request.serverPort == 80)
        return "${request.scheme}://${request.serverName}" + if (defaultPort) "" else ":${request.serverPort}"
    }
}
