package projects.kotlin.quizabfahrt.application.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import projects.kotlin.quizabfahrt.application.service.parser.BoardFile
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.application.service.parser.QuizParseException
import projects.kotlin.quizabfahrt.model.Board
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.Normalizer

@Service
class QuizService(
    private val compiler: CompilationEngine,
    private val quizStore: QuizStore,
    @Value("\${quiz.upload-dir}") uploadDir: String,
) {

    val imageDir: Path = Paths.get(uploadDir).toAbsolutePath().normalize()

    val bundledImages: List<String> by lazy {
        PathMatchingResourcePatternResolver()
            .getResources("classpath:static/images/*")
            .mapNotNull { it.filename?.let(::nfc) }
            .sorted()
    }

    private val naturalOrder = compareBy<String> { name ->
        name.lowercase().replace(Regex("\\d+")) { it.value.padStart(10, '0') }
    }

    init {
        Files.createDirectories(imageDir)
    }

    fun loadBundledQuiz() {
        val files = PathMatchingResourcePatternResolver()
            .getResources("classpath:boards/*.txt")
            .filter { it.filename != null }
            .sortedWith(compareBy(naturalOrder) { it.filename!! })
            .map { BoardFile(it.filename!!, it.inputStream.use { s -> String(s.readAllBytes(), Charsets.UTF_8) }) }

        if (files.isNotEmpty()) quizStore.replace(compiler.compile(files))
    }


    fun createQuiz(boardUploads: List<MultipartFile>, imageUploads: List<MultipartFile>): List<String> {
        val boardFiles = boardUploads
            .filter { !it.isEmpty }
            .sortedWith(compareBy(naturalOrder) { it.originalFilename ?: "" })
        val images = imageUploads.filter { !it.isEmpty }

        if (boardFiles.isEmpty()) return listOf("Bitte mindestens eine Board-Datei (.txt) hochladen.")

        val imageNames = images.map { nfc(it.originalFilename ?: "") }
        val invalidNames = imageNames.filter { it.isBlank() || Paths.get(it).fileName.toString() != it }
        if (invalidNames.isNotEmpty()) return listOf("Ungültige Bilddateinamen: ${invalidNames.joinToString()}")

        val boards = try {
            compiler.compile(boardFiles.map {
                BoardFile(it.originalFilename ?: "Board", String(it.bytes, Charsets.UTF_8))
            })
        } catch (e: QuizParseException) {
            return listOf("Fehler in Board-Datei – ${e.message}")
        }

        val available = imageNames.toSet()
        val missing = referencedImages(boards)
            .filter { it !in available && !ClassPathResource("static/images/$it").exists() }
        if (missing.isNotEmpty()) {
            return listOf("Folgende Bilder werden in den Boards verwendet, wurden aber nicht hochgeladen: ${missing.joinToString()}")
        }

        replaceStoredImages(images.zip(imageNames))
        quizStore.replace(boards)
        return emptyList()
    }

    private fun referencedImages(boards: List<Board>): List<String> {
        return boards
            .flatMap { it.categories }
            .flatMap { it.questions }
            .flatMap { listOf(it.img, it.answer.img) }
            .filterNotNull()
            .map(::nfc)
            .distinct()
    }

    private fun replaceStoredImages(images: List<Pair<MultipartFile, String>>) {
        Files.list(imageDir).use { old -> old.forEach(Files::deleteIfExists) }
        for ((file, name) in images) {
            file.transferTo(imageDir.resolve(name))
        }
    }

    private fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
}
