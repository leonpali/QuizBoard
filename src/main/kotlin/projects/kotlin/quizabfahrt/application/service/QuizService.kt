package projects.kotlin.quizabfahrt.application.service

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import projects.kotlin.quizabfahrt.application.service.parser.BoardFile
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.application.service.parser.QuizParseException
import projects.kotlin.quizabfahrt.application.session.QuizSession
import projects.kotlin.quizabfahrt.application.session.QuizSessionException
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.Board
import java.nio.file.Paths
import java.text.Normalizer

@Service
class QuizService(
    private val compiler: CompilationEngine,
    private val sessions: QuizSessionService,
) {

    val bundledImages: List<String> by lazy {
        PathMatchingResourcePatternResolver()
            .getResources("classpath:static/images/*")
            .mapNotNull { it.filename?.let { name -> nfc(name) } }
            .sorted()
    }

    private val naturalOrder = compareBy<String> { name ->
        name.lowercase().replace(Regex("\\d+")) { it.value.padStart(10, '0') }
    }

    /**
     * Parses the uploaded board files, checks that every referenced image is available
     * and starts a new quiz session with them.
     * Throws [QuizSessionException] with a user-facing message if the upload is invalid.
     */
    fun createQuiz(
        boardUploads: List<MultipartFile>,
        imageUploads: List<MultipartFile>,
        previousHostToken: String?,
    ): QuizSession {
        val boardFiles = boardUploads
            .filter { !it.isEmpty }
            .sortedWith(compareBy(naturalOrder) { it.originalFilename ?: "" })
        val images = imageUploads.filter { !it.isEmpty }

        if (boardFiles.isEmpty()) throw QuizSessionException("Bitte mindestens eine Board-Datei (.txt) hochladen.")

        val imageNames = images.map { nfc(it.originalFilename ?: "") }
        val invalidNames = imageNames.filter { !isPlainFileName(it) }
        if (invalidNames.isNotEmpty()) throw QuizSessionException("Ungültige Bilddateinamen: ${invalidNames.joinToString()}")

        val boards = try {
            compiler.compile(boardFiles.map {
                BoardFile(it.originalFilename ?: "Board", String(it.bytes, Charsets.UTF_8))
            })
        } catch (e: QuizParseException) {
            throw QuizSessionException("Fehler in Board-Datei – ${e.message}")
        }

        val available = imageNames.toSet()
        val missing = referencedImages(boards)
            .filter { it !in available && !ClassPathResource("static/images/$it").exists() }
        if (missing.isNotEmpty()) {
            throw QuizSessionException("Folgende Bilder werden in den Boards verwendet, wurden aber nicht hochgeladen: ${missing.joinToString()}")
        }

        return sessions.create(boards, previousHostToken) { dir ->
            for ((file, name) in images.zip(imageNames)) {
                file.transferTo(dir.resolve(name))
            }
        }
    }

    private fun referencedImages(boards: List<Board>): List<String> {
        return boards
            .flatMap { it.categories }
            .flatMap { it.questions }
            .flatMap { listOf(it.img, it.answer.img) }
            .filterNotNull()
            .map { nfc(it) }
            .distinct()
    }

    companion object {
        fun isPlainFileName(name: String): Boolean =
            name.isNotBlank() && name != "." && name != ".." && Paths.get(name).fileName.toString() == name

        // Uploaded file names may arrive decomposed (e.g. from macOS), board files usually composed
        fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
    }
}
