package projects.kotlin.quizabfahrt.application.editor

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.application.service.QuizService
import projects.kotlin.quizabfahrt.application.service.parser.BoardFile
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine
import projects.kotlin.quizabfahrt.application.service.parser.QuizParseException
import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class BoardEditorService(private val compiler: CompilationEngine) {

    companion object {
        const val MAX_CATEGORIES = 12
        const val MAX_QUESTIONS = 12
        const val MAX_TEXT_LENGTH = 1000
        const val MAX_NAME_LENGTH = 100
        const val MAX_POINTS = 100_000
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
        questions.add(QuestionDraft(points = (questions.size + 1) * 100))
    }

    fun removeQuestion(draft: BoardDraft, categoryIndex: Int, questionIndex: Int) = change(draft) {
        draft.getCategory(categoryIndex).questions.remove(draft.getQuestion(categoryIndex, questionIndex))
    }

    fun updateQuestion(draft: BoardDraft, categoryIndex: Int, questionIndex: Int, input: QuestionInput) = change(draft) {
        val question = draft.getQuestion(categoryIndex, questionIndex)
        val type = input.type
        val cleanText = clean(input.text, "Der Text", MAX_TEXT_LENGTH)
        val cleanImg = cleanImageName(input.img)
        val cleanPoints = input.points.trim().toIntOrNull()?.takeIf { it in 0..MAX_POINTS }
            ?: throw BoardEditorException("Die Punkte müssen eine ganze Zahl zwischen 0 und $MAX_POINTS sein.")

        if (type != EQuestionTypes.IMAGE && cleanText == null) throw BoardEditorException("Diese Frage braucht einen Text.")
        if (type == EQuestionTypes.IMAGE && cleanImg == null) throw BoardEditorException("Eine Bildfrage braucht einen Bildnamen.")

        question.type = type
        question.text = cleanText
        question.img = cleanImg
        question.points = cleanPoints
    }

    fun updateAnswer(draft: BoardDraft, categoryIndex: Int, questionIndex: Int, input: AnswerInput) = change(draft) {
        val question = draft.getQuestion(categoryIndex, questionIndex)
        val type = input.type
        val cleanText = clean(input.text, "Der Text", MAX_TEXT_LENGTH)
        val cleanImg = cleanImageName(input.img)

        if (type != EAnswerTypes.IMAGE && cleanText == null) throw BoardEditorException("Diese Antwort braucht einen Text.")
        if (type != EAnswerTypes.TEXT && cleanImg == null) throw BoardEditorException("Diese Antwort braucht einen Bildnamen.")

        question.answerType = type
        question.answerText = cleanText
        question.answerImg = cleanImg
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

    private fun cleanImageName(value: String?): String? {
        val name = clean(value, "Der Bildname", MAX_NAME_LENGTH)?.let { QuizService.nfc(it) } ?: return null
        if (!QuizService.isPlainFileName(name)) throw BoardEditorException("Der Bildname darf keine Ordner enthalten.")
        return name
    }

    private fun cleanFileName(value: String?): String {
        val name = value?.trim()?.let { QuizService.nfc(it) }
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH && !LINE_BREAKS.containsMatchIn(it) && QuizService.isPlainFileName(it) }
            ?: return DEFAULT_FILE_NAME
        return if (name.endsWith(".txt", ignoreCase = true)) name else "$name.txt"
    }
}
