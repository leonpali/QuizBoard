package projects.kotlin.quizabfahrt.application.editor

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.application.service.QuizService
import projects.kotlin.quizabfahrt.application.service.parser.BoardFile
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.application.service.parser.QuizParseException
import projects.kotlin.quizabfahrt.model.Content
import projects.kotlin.quizabfahrt.model.ContentException
import projects.kotlin.quizabfahrt.model.ContentField
import projects.kotlin.quizabfahrt.model.ContentType
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import projects.kotlin.quizabfahrt.model.Question
import projects.kotlin.quizabfahrt.model.QuestionSetting
import projects.kotlin.quizabfahrt.model.QuestionSettings
import projects.kotlin.quizabfahrt.model.SettingException
import projects.kotlin.quizabfahrt.model.typeOf
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class BoardEditorService(private val compiler: CompilationEngine) {

    companion object {
        const val MAX_CATEGORIES = 12
        const val MAX_QUESTIONS = 12
        const val MAX_NAME_LENGTH = 100
        const val DEFAULT_FILE_NAME = "Board.txt"
        val DRAFT_TTL: Duration = Duration.ofHours(6)
        const val MAX_UPLOAD_BYTES = 1_000_000
        private const val MAX_DRAFTS = 50
        private const val MAX_DRAFTS_PER_IP = 10
        private val LINE_BREAKS = Regex("[\\p{Cc}\\u2028\\u2029]+")
    }

    private val drafts = ConcurrentHashMap<String, BoardDraft>()

    fun create(previousToken: String?, clientIp: String): BoardDraft {
        findDraft(previousToken)?.let(::discard)
        removeExpired()
        if (drafts.size >= MAX_DRAFTS) {
            throw BoardEditorException("Es werden gerade zu viele Boards bearbeitet. Bitte später erneut versuchen.")
        }
        if (drafts.values.count { it.creatorIp == clientIp } >= MAX_DRAFTS_PER_IP) {
            throw BoardEditorException("Aus diesem Netzwerk werden schon zu viele Boards bearbeitet. Bitte später erneut versuchen.")
        }
        val draft = BoardDraft(UUID.randomUUID().toString(), clientIp, DEFAULT_FILE_NAME, Instant.now())
        drafts[draft.token] = draft
        return draft
    }

    fun upload(fileName: String?, content: ByteArray, previousToken: String?, clientIp: String): BoardDraft {
        if (content.isEmpty()) throw BoardEditorException("Bitte eine Board-Datei (.txt) auswählen.")
        if (content.size > MAX_UPLOAD_BYTES) throw BoardEditorException("Die Datei ist zu groß.")

        val name = cleanFileName(fileName)
        val board = try {
            compiler.compile(listOf(BoardFile(name, String(content, Charsets.UTF_8))), requireFullCategories = false).single()
        } catch (e: QuizParseException) {
            throw BoardEditorException("Fehler in Board-Datei – ${e.message}")
        }
        if (board.categories.size > MAX_CATEGORIES || board.categories.any { it.questions.size > MAX_QUESTIONS }) {
            throw BoardEditorException("Das Board hat mehr als $MAX_CATEGORIES Kategorien oder $MAX_QUESTIONS Fragen pro Kategorie.")
        }

        val draft = create(previousToken, clientIp)
        synchronized(draft) {
            draft.fileName = name
            draft.addFrom(board)
        }
        return draft
    }

    fun findDraft(token: String?): BoardDraft? =
        token?.let { drafts[it] }?.takeUnless { isExpired(it, Instant.now()) }

    fun requireDraft(token: String?): BoardDraft = findDraft(token) ?: throw NoDraftException()

    fun addCategory(draft: BoardDraft, name: String) = change(draft) {
        if (draft.categories.size >= MAX_CATEGORIES) throw BoardEditorException("Maximal $MAX_CATEGORIES Kategorien.")
        draft.categories.add(CategoryDraft(requireText(name, "Der Name", MAX_NAME_LENGTH)))
    }

    fun renameCategory(draft: BoardDraft, index: Int, name: String) = change(draft) {
        draft.getCategory(index).name = requireText(name, "Der Name", MAX_NAME_LENGTH)
    }

    fun removeCategory(draft: BoardDraft, index: Int) = change(draft) {
        draft.categories.remove(draft.getCategory(index))
    }

    fun moveCategory(draft: BoardDraft, index: Int, direction: MoveDirection) = change(draft) {
        draft.categories.move(index, direction)
    }

    fun moveQuestion(draft: BoardDraft, categoryIndex: Int, questionIndex: Int, direction: MoveDirection) = change(draft) {
        draft.getCategory(categoryIndex).questions.move(questionIndex, direction)
    }

    fun addQuestion(draft: BoardDraft, categoryIndex: Int) = change(draft) {
        val questions = draft.getCategory(categoryIndex).questions
        if (questions.size >= MAX_QUESTIONS) throw BoardEditorException("Maximal $MAX_QUESTIONS Fragen pro Kategorie.")
        questions.add(newQuestion(points = (questions.size + 1) * 100))
    }

    fun removeQuestion(draft: BoardDraft, categoryIndex: Int, questionIndex: Int) = change(draft) {
        draft.removeQuestion(categoryIndex, questionIndex)
    }

    fun updateQuestion(draft: BoardDraft, categoryIndex: Int, questionIndex: Int, content: ContentInput, settings: SettingsInput) =
        change(draft) {
            val question = draft.getQuestion(categoryIndex, questionIndex)
            val changed = question.copy(content = buildContent<EQuestionTypes>(content), settings = buildSettings(settings))
            draft.setQuestion(categoryIndex, questionIndex, changed)
        }

    fun updateAnswer(draft: BoardDraft, categoryIndex: Int, questionIndex: Int, input: ContentInput) = change(draft) {
        val question = draft.getQuestion(categoryIndex, questionIndex)
        draft.setQuestion(categoryIndex, questionIndex, question.copy(answer = buildContent<EAnswerTypes>(input)))
    }

    // Downloading ends the draft: nothing is kept once the file has been handed out
    fun download(draft: BoardDraft, fileName: String?): Pair<String, String> {
        val result = synchronized(draft) { cleanFileName(fileName ?: draft.fileName) to draft.toText() }
        discard(draft)
        return result
    }

    fun discard(draft: BoardDraft) {
        drafts.remove(draft.token, draft)
    }

    @Scheduled(fixedDelayString = "PT5M")
    fun removeExpired() {
        val now = Instant.now()
        drafts.values.filter { isExpired(it, now) }.forEach(::discard)
    }

    private fun isExpired(draft: BoardDraft, now: Instant) = now.isAfter(draft.lastChanged + DRAFT_TTL)

    private fun change(draft: BoardDraft, block: () -> Unit) {
        synchronized(draft) {
            block()
            draft.lastChanged = Instant.now()
        }
    }

    // The board format is line based, so values must stay on one line. The parser also splits lines
    // at U+2028, U+2029 and U+0085, which would let a value inject its own keys into the file.
    private fun clean(value: String?, label: String, maxLength: Int): String? {
        val cleaned = value?.replace(LINE_BREAKS, " ")?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        if (cleaned != null && cleaned.length > maxLength) throw BoardEditorException("$label darf höchstens $maxLength Zeichen lang sein.")
        return cleaned
    }

    private fun requireText(value: String, label: String, maxLength: Int): String =
        clean(value, label, maxLength) ?: throw BoardEditorException("$label darf nicht leer sein.")

    private fun newQuestion(points: Int) = Question(
        Content.of(EQuestionTypes.TEXT, mapOf(ContentField.TEXT to "Neue Frage")),
        Content.of(EAnswerTypes.TEXT, mapOf(ContentField.TEXT to "Antwort")),
        QuestionSettings.of(mapOf(QuestionSetting.Points.key to points.toString())),
    )

    private fun buildSettings(input: SettingsInput): QuestionSettings = try {
        QuestionSettings.of(input.values)
    } catch (e: SettingException) {
        throw BoardEditorException(e.message.orEmpty())
    }

    private inline fun <reified T> buildContent(input: ContentInput): Content<T> where T : Enum<T>, T : ContentType {
        val type = typeOf<T>(input.typeKey) ?: throw BoardEditorException("Unbekannter Typ '${input.typeKey}'.")
        val values = input.getValues().mapValues { (field, value) -> cleanField(field, value) }
        return try {
            Content.of(type, values)
        } catch (e: ContentException) {
            throw BoardEditorException(e.message.orEmpty())
        }
    }

    private fun cleanField(field: ContentField, value: String?): String? {
        if (!field.isFile) return clean(value, field.label, field.maxLength)
        val name = clean(value, field.label, field.maxLength)?.let { QuizService.nfc(it) } ?: return null
        if (!QuizService.isPlainFileName(name)) throw BoardEditorException("${field.label}: Der Dateiname darf keine Ordner enthalten.")
        return name
    }

    private fun cleanFileName(value: String?): String {
        val name = value?.trim()?.let { QuizService.nfc(it) }
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH && !LINE_BREAKS.containsMatchIn(it) && QuizService.isPlainFileName(it) }
            ?: return DEFAULT_FILE_NAME
        return if (name.endsWith(".txt", ignoreCase = true)) name else "$name.txt"
    }
}
