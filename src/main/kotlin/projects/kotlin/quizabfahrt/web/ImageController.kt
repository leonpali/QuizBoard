package projects.kotlin.quizabfahrt.web

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import projects.kotlin.quizabfahrt.application.service.QuizService
import projects.kotlin.quizabfahrt.application.session.QuizSessionService

@Controller
class ImageController(private val sessions: QuizSessionService) {

    @GetMapping("/images/{filename}")
    fun image(
        @PathVariable filename: String,
        @CookieValue(SessionCookies.HOST, required = false) hostToken: String?,
    ): ResponseEntity<Resource> {
        val name = QuizService.nfc(filename)
        if (!QuizService.isPlainFileName(name)) return ResponseEntity.notFound().build()

        val uploaded = sessions.findByHostToken(hostToken)
            ?.let { FileSystemResource(it.imageDir.resolve(name)) }
            ?.takeIf { it.exists() }
        val resource = uploaded
            ?: ClassPathResource("static/images/$name").takeIf { it.exists() }
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .contentType(MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM))
            .body(resource)
    }
}
