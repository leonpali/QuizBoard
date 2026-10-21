package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import java.time.Duration
import java.time.Instant

object SessionCookies {

    const val HOST = "quiz_host"
    const val PLAYER = "quiz_player"

    fun set(request: HttpServletRequest, response: HttpServletResponse, name: String, value: String, expiresAt: Instant) {
        write(response, request, name, value, Duration.between(Instant.now(), expiresAt))
    }

    fun clear(request: HttpServletRequest, response: HttpServletResponse, name: String) {
        write(response, request, name, "", Duration.ZERO)
    }

    private fun write(response: HttpServletResponse, request: HttpServletRequest, name: String, value: String, maxAge: Duration) {
        val cookie = ResponseCookie.from(name, value)
            .path("/")
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
            .maxAge(maxAge)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }
}
