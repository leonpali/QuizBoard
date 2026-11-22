package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import projects.kotlin.quizabfahrt.application.editor.BoardDraft
import projects.kotlin.quizabfahrt.application.editor.BoardEditorException
import projects.kotlin.quizabfahrt.application.editor.BoardEditorService
import projects.kotlin.quizabfahrt.application.editor.ContentInput
import projects.kotlin.quizabfahrt.application.editor.FormField
import projects.kotlin.quizabfahrt.application.editor.MoveDirection
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.model.ContentType
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EInputTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import java.time.Duration
import java.time.Instant

@Controller
@RequestMapping("/editor")
class EditorController(private val editor: BoardEditorService) {

    @GetMapping
    fun start(model: Model, @RequestParam(required = false) downloaded: String?, @EditorDraft draft: BoardDraft?): String {
        model.addAttribute("draft", draft)
        model.addAttribute("downloaded", downloaded != null)
        model.addAttribute("ttlHours", BoardEditorService.DRAFT_TTL.toHours())
        return "editor"
    }

    @PostMapping("/new")
    fun newBoard(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @EditorDraft previous: BoardDraft?,
    ): String = startDraft(model, request, response, previous) { editor.create(previous?.token, request.remoteAddr) }

    @PostMapping("/upload")
    fun upload(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam("board", required = false) file: MultipartFile?,
        @EditorDraft previous: BoardDraft?,
    ): String = startDraft(model, request, response, previous) {
        // Read at most one byte over the limit, so a large upload is never loaded into memory completely
        val content = file?.inputStream?.use { it.readNBytes(BoardEditorService.MAX_UPLOAD_BYTES + 1) } ?: ByteArray(0)
        editor.upload(file?.originalFilename, content, previous?.token, request.remoteAddr)
    }

    @GetMapping("/board")
    fun board(model: Model, @EditorDraft draft: BoardDraft): String {
        synchronized(draft) {
            model.addAttribute("draft", draft)
            model.addAttribute("categories", draft.categories.toList())
            model.addAttribute("incomplete", draft.getIncompleteCategories().map { it.name })
        }
        model.addAttribute("questionsPerCategory", CompilationEngine.QUESTIONS_PER_CATEGORY)
        model.addAttribute("maxCategories", BoardEditorService.MAX_CATEGORIES)
        model.addAttribute("maxNameLength", BoardEditorService.MAX_NAME_LENGTH)
        return "editorBoard"
    }

    @PostMapping("/categories")
    fun addCategory(model: Model, @RequestParam name: String, @EditorDraft draft: BoardDraft): String =
        tryChange(model, { board(model, draft) }) { editor.addCategory(draft, name) } ?: "redirect:/editor/board"

    @PostMapping("/categories/{c}/delete")
    fun removeCategory(@PathVariable c: Int, @EditorDraft draft: BoardDraft): String {
        editor.removeCategory(draft, c)
        return "redirect:/editor/board"
    }

    @PostMapping("/categories/{c}/move")
    fun moveCategory(@PathVariable c: Int, @RequestParam direction: MoveDirection, @EditorDraft draft: BoardDraft): String {
        editor.moveCategory(draft, c, direction)
        return "redirect:/editor/board"
    }

    @GetMapping("/categories/{c}")
    fun category(model: Model, @PathVariable c: Int, @EditorDraft draft: BoardDraft): String {
        synchronized(draft) {
            val category = draft.getCategory(c)
            model.addAttribute("c", c)
            model.addAttribute("category", category)
            model.addAttribute("questions", category.questions.toList())
        }
        model.addAttribute("questionsPerCategory", CompilationEngine.QUESTIONS_PER_CATEGORY)
        model.addAttribute("maxQuestions", BoardEditorService.MAX_QUESTIONS)
        model.addAttribute("maxNameLength", BoardEditorService.MAX_NAME_LENGTH)
        return "editorCategory"
    }

    @PostMapping("/categories/{c}/rename")
    fun renameCategory(model: Model, @PathVariable c: Int, @RequestParam name: String, @EditorDraft draft: BoardDraft): String =
        tryChange(model, { category(model, c, draft) }) { editor.renameCategory(draft, c, name) } ?: "redirect:/editor/categories/$c"

    @PostMapping("/categories/{c}/questions")
    fun addQuestion(model: Model, @PathVariable c: Int, @EditorDraft draft: BoardDraft): String =
        tryChange(model, { category(model, c, draft) }) { editor.addQuestion(draft, c) } ?: "redirect:/editor/categories/$c"

    @PostMapping("/categories/{c}/questions/{q}/delete")
    fun removeQuestion(@PathVariable c: Int, @PathVariable q: Int, @EditorDraft draft: BoardDraft): String {
        editor.removeQuestion(draft, c, q)
        return "redirect:/editor/categories/$c"
    }

    @PostMapping("/categories/{c}/questions/{q}/move")
    fun moveQuestion(
        @PathVariable c: Int,
        @PathVariable q: Int,
        @RequestParam direction: MoveDirection,
        @EditorDraft draft: BoardDraft,
    ): String {
        editor.moveQuestion(draft, c, q, direction)
        return "redirect:/editor/categories/$c"
    }

    @GetMapping("/categories/{c}/questions/{q}/question")
    fun questionForm(model: Model, @PathVariable c: Int, @PathVariable q: Int, @EditorDraft draft: BoardDraft): String {
        val question = synchronized(draft) { draft.getQuestion(c, q) }
        return questionPage(model, draft, c, q, ContentInput.of(question))
    }

    @PostMapping("/categories/{c}/questions/{q}/question")
    fun saveQuestion(
        model: Model,
        @PathVariable c: Int,
        @PathVariable q: Int,
        @RequestParam params: Map<String, String>,
        @EditorDraft draft: BoardDraft,
    ): String {
        val form = ContentInput.fromForm(params)
        return tryChange(model, { questionPage(model, draft, c, q, form) }) { editor.updateQuestion(draft, c, q, form) }
            ?: "redirect:/editor/categories/$c"
    }

    @GetMapping("/categories/{c}/questions/{q}/answer")
    fun answerForm(model: Model, @PathVariable c: Int, @PathVariable q: Int, @EditorDraft draft: BoardDraft): String {
        val question = synchronized(draft) { draft.getQuestion(c, q) }
        return answerPage(model, draft, c, q, ContentInput.of(question.answer))
    }

    @PostMapping("/categories/{c}/questions/{q}/answer")
    fun saveAnswer(
        model: Model,
        @PathVariable c: Int,
        @PathVariable q: Int,
        @RequestParam params: Map<String, String>,
        @EditorDraft draft: BoardDraft,
    ): String {
        val form = ContentInput.fromForm(params)
        return tryChange(model, { answerPage(model, draft, c, q, form) }) { editor.updateAnswer(draft, c, q, form) }
            ?: "redirect:/editor/categories/$c"
    }

    @PostMapping("/download")
    fun download(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam(required = false) fileName: String?,
        @EditorDraft draft: BoardDraft,
    ): ResponseEntity<String> {
        val (name, content) = editor.download(draft, fileName)
        SessionCookies.clear(request, response, SessionCookies.EDITOR)
        return ResponseEntity.ok()
            .contentType(MediaType("text", "plain", Charsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, Charsets.UTF_8).build().toString())
            .body(content)
    }

    @PostMapping("/discard")
    fun discard(request: HttpServletRequest, response: HttpServletResponse, @EditorDraft draft: BoardDraft?): String {
        draft?.let(editor::discard)
        SessionCookies.clear(request, response, SessionCookies.EDITOR)
        return "redirect:/editor"
    }

    private fun startDraft(
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
        previous: BoardDraft?,
        create: () -> BoardDraft,
    ): String {
        val draft = try {
            create()
        } catch (e: BoardEditorException) {
            model.addAttribute("error", e.message)
            return start(model, null, previous)
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

    private fun questionPage(model: Model, draft: BoardDraft, c: Int, q: Int, form: ContentInput) =
        contentPage(model, draft, c, form, EQuestionTypes.entries, "Frage ${q + 1} bearbeiten", "/editor/categories/$c/questions/$q/question", forQuestion = true)

    private fun answerPage(model: Model, draft: BoardDraft, c: Int, q: Int, form: ContentInput) =
        contentPage(model, draft, c, form, EAnswerTypes.entries, "Antwort zu Frage ${q + 1} bearbeiten", "/editor/categories/$c/questions/$q/answer", forQuestion = false)

    private fun contentPage(
        model: Model, draft: BoardDraft, c: Int, form: ContentInput,
        types: List<ContentType>, title: String, action: String, forQuestion: Boolean,
    ): String {
        model.addAttribute("categoryName", synchronized(draft) { draft.getCategory(c).name })
        model.addAttribute("c", c)
        model.addAttribute("title", title)
        model.addAttribute("action", action)
        model.addAttribute("form", form)
        model.addAttribute("types", types)
        model.addAttribute("typeHints", types.mapNotNull { type -> type.hint?.let { "${type.label}: $it" } })
        model.addAttribute("fields", FormField.of(types))
        model.addAttribute("forQuestion", forQuestion)
        model.addAttribute("inputs", EInputTypes.entries)
        model.addAttribute("maxPoints", BoardEditorService.MAX_POINTS)
        return "editorContent"
    }
}
