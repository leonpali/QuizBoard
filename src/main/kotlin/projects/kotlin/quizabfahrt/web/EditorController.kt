package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import projects.kotlin.quizabfahrt.application.editor.BoardDraft
import projects.kotlin.quizabfahrt.application.editor.BoardEditorException
import projects.kotlin.quizabfahrt.application.editor.BoardEditorService
import projects.kotlin.quizabfahrt.application.editor.MoveDirection
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import java.time.Duration
import java.time.Instant

@Controller
@RequestMapping("/editor")
class EditorController(private val editor: BoardEditorService) {

    @GetMapping
    fun start(
        model: Model,
        @RequestParam(required = false) downloaded: String?,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        model.addAttribute("draft", editor.findDraft(token))
        model.addAttribute("downloaded", downloaded != null)
        model.addAttribute("ttlHours", BoardEditorService.DRAFT_TTL.toHours())
        return "editor"
    }

    @PostMapping("/new")
    fun newBoard(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String = startDraft(model, request, response, token) { editor.create(token, request.remoteAddr) }

    @PostMapping("/upload")
    fun upload(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam("board", required = false) file: MultipartFile?,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String = startDraft(model, request, response, token) {
        // Read at most one byte over the limit, so a large upload is never loaded into memory completely
        val content = file?.inputStream?.use { it.readNBytes(BoardEditorService.MAX_UPLOAD_BYTES + 1) } ?: ByteArray(0)
        editor.upload(file?.originalFilename, content, token, request.remoteAddr)
    }

    @GetMapping("/board")
    fun board(model: Model, @CookieValue(SessionCookies.EDITOR, required = false) token: String?): String {
        val draft = editor.requireDraft(token)
        synchronized(draft) {
            model.addAttribute("draft", draft)
            model.addAttribute("categories", draft.categories.toList())
            model.addAttribute("incomplete", draft.getIncompleteCategories().map { it.name })
        }
        model.addAttribute("questionsPerCategory", CompilationEngine.QUESTIONS_PER_CATEGORY)
        model.addAttribute("maxCategories", BoardEditorService.MAX_CATEGORIES)
        return "editorBoard"
    }

    @PostMapping("/categories")
    fun addCategory(
        model: Model,
        @RequestParam name: String,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        val draft = editor.requireDraft(token)
        return tryChange(model, { board(model, token) }) { editor.addCategory(draft, name) } ?: "redirect:/editor/board"
    }

    @PostMapping("/categories/{c}/delete")
    fun removeCategory(@PathVariable c: Int, @CookieValue(SessionCookies.EDITOR, required = false) token: String?): String {
        editor.removeCategory(editor.requireDraft(token), c)
        return "redirect:/editor/board"
    }

    @PostMapping("/categories/{c}/move")
    fun moveCategory(
        @PathVariable c: Int,
        @RequestParam direction: MoveDirection,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        editor.moveCategory(editor.requireDraft(token), c, direction)
        return "redirect:/editor/board"
    }

    @GetMapping("/categories/{c}")
    fun category(model: Model, @PathVariable c: Int, @CookieValue(SessionCookies.EDITOR, required = false) token: String?): String {
        val draft = editor.requireDraft(token)
        synchronized(draft) {
            val category = draft.getCategory(c)
            model.addAttribute("c", c)
            model.addAttribute("category", category)
            model.addAttribute("questions", category.questions.toList())
        }
        model.addAttribute("questionsPerCategory", CompilationEngine.QUESTIONS_PER_CATEGORY)
        model.addAttribute("maxQuestions", BoardEditorService.MAX_QUESTIONS)
        return "editorCategory"
    }

    @PostMapping("/categories/{c}/rename")
    fun renameCategory(
        model: Model,
        @PathVariable c: Int,
        @RequestParam name: String,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        val draft = editor.requireDraft(token)
        return tryChange(model, { category(model, c, token) }) { editor.renameCategory(draft, c, name) }
            ?: "redirect:/editor/categories/$c"
    }

    @PostMapping("/categories/{c}/questions")
    fun addQuestion(model: Model, @PathVariable c: Int, @CookieValue(SessionCookies.EDITOR, required = false) token: String?): String {
        val draft = editor.requireDraft(token)
        return tryChange(model, { category(model, c, token) }) { editor.addQuestion(draft, c) }
            ?: "redirect:/editor/categories/$c"
    }

    @PostMapping("/categories/{c}/questions/{q}/delete")
    fun removeQuestion(
        @PathVariable c: Int,
        @PathVariable q: Int,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        editor.removeQuestion(editor.requireDraft(token), c, q)
        return "redirect:/editor/categories/$c"
    }

    @PostMapping("/categories/{c}/questions/{q}/move")
    fun moveQuestion(
        @PathVariable c: Int,
        @PathVariable q: Int,
        @RequestParam direction: MoveDirection,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        editor.moveQuestion(editor.requireDraft(token), c, q, direction)
        return "redirect:/editor/categories/$c"
    }

    @GetMapping("/categories/{c}/questions/{q}/question")
    fun questionForm(
        model: Model,
        @PathVariable c: Int,
        @PathVariable q: Int,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        val draft = editor.requireDraft(token)
        val question = synchronized(draft) { draft.getQuestion(c, q) }
        return questionPage(model, draft, c, q, question.type, question.text, question.img, question.points.toString())
    }

    @PostMapping("/categories/{c}/questions/{q}/question")
    fun saveQuestion(
        model: Model,
        @PathVariable c: Int,
        @PathVariable q: Int,
        @RequestParam type: EQuestionTypes,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) img: String?,
        @RequestParam points: String,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        val draft = editor.requireDraft(token)
        return tryChange(model, { questionPage(model, draft, c, q, type, text, img, points) }) {
            editor.updateQuestion(draft, c, q, type, text, img, points)
        } ?: "redirect:/editor/categories/$c"
    }

    @GetMapping("/categories/{c}/questions/{q}/answer")
    fun answerForm(
        model: Model,
        @PathVariable c: Int,
        @PathVariable q: Int,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        val draft = editor.requireDraft(token)
        val question = synchronized(draft) { draft.getQuestion(c, q) }
        return answerPage(model, draft, c, q, question.answerType, question.answerText, question.answerImg)
    }

    @PostMapping("/categories/{c}/questions/{q}/answer")
    fun saveAnswer(
        model: Model,
        @PathVariable c: Int,
        @PathVariable q: Int,
        @RequestParam type: EAnswerTypes,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) img: String?,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        val draft = editor.requireDraft(token)
        return tryChange(model, { answerPage(model, draft, c, q, type, text, img) }) {
            editor.updateAnswer(draft, c, q, type, text, img)
        } ?: "redirect:/editor/categories/$c"
    }

    @PostMapping("/download")
    fun download(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam(required = false) fileName: String?,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): ResponseEntity<String> {
        val (name, content) = editor.download(editor.requireDraft(token), fileName)
        SessionCookies.clear(request, response, SessionCookies.EDITOR)
        return ResponseEntity.ok()
            .contentType(MediaType("text", "plain", Charsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, Charsets.UTF_8).build().toString())
            .body(content)
    }

    @PostMapping("/discard")
    fun discard(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @CookieValue(SessionCookies.EDITOR, required = false) token: String?,
    ): String {
        editor.findDraft(token)?.let(editor::discard)
        SessionCookies.clear(request, response, SessionCookies.EDITOR)
        return "redirect:/editor"
    }

    private fun startDraft(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        token: String?,
        create: () -> BoardDraft,
    ): String {
        val draft = try {
            create()
        } catch (e: BoardEditorException) {
            model.addAttribute("error", e.message)
            return start(model, null, token)
        }
        SessionCookies.set(request, response, SessionCookies.EDITOR, draft.token, Instant.now().plus(Duration.ofDays(1)))
        return "redirect:/editor/board"
    }

    private fun tryChange(model: Model, page: () -> String, change: () -> Unit): String? = try {
        change()
        null
    } catch (e: BoardEditorException) {
        model.addAttribute("error", e.message)
        page()
    }

    private fun questionPage(
        model: Model, draft: BoardDraft, c: Int, q: Int,
        type: EQuestionTypes, text: String?, img: String?, points: String,
    ): String {
        model.addAttribute("categoryName", synchronized(draft) { draft.getCategory(c).name })
        model.addAttribute("c", c)
        model.addAttribute("q", q)
        model.addAttribute("types", EQuestionTypes.entries)
        model.addAttribute("type", type)
        model.addAttribute("text", text.orEmpty())
        model.addAttribute("img", img.orEmpty())
        model.addAttribute("points", points)
        return "editorQuestion"
    }

    private fun answerPage(
        model: Model, draft: BoardDraft, c: Int, q: Int,
        type: EAnswerTypes, text: String?, img: String?,
    ): String {
        model.addAttribute("categoryName", synchronized(draft) { draft.getCategory(c).name })
        model.addAttribute("c", c)
        model.addAttribute("q", q)
        model.addAttribute("types", EAnswerTypes.entries)
        model.addAttribute("type", type)
        model.addAttribute("text", text.orEmpty())
        model.addAttribute("img", img.orEmpty())
        return "editorAnswer"
    }
}
