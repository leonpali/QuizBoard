package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = ["quiz.max-sessions=100", "quiz.max-players=3"])
@AutoConfigureMockMvc
class QuizFlowTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    companion object {
        private val ipCounter = AtomicInteger()
    }

    // Every test gets its own client address, so the join throttle of one test doesn't affect others
    private val ip = RequestPostProcessor { it.remoteAddr = "10.1.0.${ipCounter.incrementAndGet()}"; it }

    private val hosts = mutableListOf<Cookie>()

    @AfterEach
    fun endQuizzes() {
        hosts.forEach { mockMvc.post("/host/end") { cookie(it) } }
    }

    private fun boardFile(fileName: String, vararg categories: String, image: String? = null): MockMultipartFile {
        val text = categories.joinToString("\n") { name ->
            "category:\n    name: $name\n    questions:\n" + (1..6).joinToString("\n") { q ->
                val isImage = image != null && q == 1
                """
                question_$q:
                    type: ${if (isImage) "img" else "text"}
                    text: $name Frage $q
                    img: ${if (isImage) image else "null"}
                    points: ${q * 100}
                    answer:
                        type: text
                        text: $name Antwort $q
                        img: null
                """.trimIndent()
            }
        }
        return MockMultipartFile("boards", fileName, "text/plain", text.toByteArray())
    }

    private fun image(name: String) = MockMultipartFile("images", name, "image/png", byteArrayOf(1, 2, 3))

    private class Quiz(val host: Cookie, val code: String)

    private fun createQuiz(vararg files: MockMultipartFile = arrayOf(boardFile("Board_1.txt", "Kat"))): Quiz {
        val response = mockMvc.multipart("/quiz") { files.forEach { file(it) } }
            .andExpect { redirectedUrl("/host") }.andReturn().response
        val host = response.getCookie(SessionCookies.HOST)!!
        hosts.add(host)
        val code = Regex("class=\"join-code\">(\\d{6})").find(page("/host", host))!!.groupValues[1]
        return Quiz(host, code)
    }

    private fun createQuizError(vararg files: MockMultipartFile): String =
        mockMvc.multipart("/quiz") { files.forEach { file(it) } }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString

    private fun join(quiz: Quiz, name: String): Cookie =
        mockMvc.post("/join") { param("code", quiz.code); param("name", name); with(ip) }
            .andExpect { redirectedUrl("/play") }.andReturn().response.getCookie(SessionCookies.PLAYER)!!

    private fun joinError(code: String, name: String): String =
        mockMvc.post("/join") { param("code", code); param("name", name); with(ip) }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString

    private fun page(path: String, cookie: Cookie): String =
        mockMvc.get(path) { cookie(cookie) }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    private fun buzz(player: Cookie): String =
        mockMvc.post("/play/buzz") { cookie(player) }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    // Question n (1-based) of the first category on the first board
    private fun q(n: Int) = "/board/1/category/1/question/$n"

    private fun openQuestion(quiz: Quiz, question: Int) {
        mockMvc.post("${q(question)}/open") { cookie(quiz.host) }
            .andExpect { redirectedUrl(q(question)) }
    }

    private fun score(quiz: Quiz, question: Int, playerId: Int, outcome: String) {
        mockMvc.post("${q(question)}/score") {
            cookie(quiz.host); param("playerId", "$playerId"); param("outcome", outcome)
        }.andExpect { redirectedUrl("${q(question)}/score") }
    }

    // Creating a quiz

    @Test
    fun `creating a quiz leads to the lobby with a join code`() {
        val quiz = createQuiz()

        val lobby = page("/host", quiz.host)
        assertTrue(quiz.code.matches(Regex("\\d{6}")))
        assertContains(lobby, "/join")
        assertContains(lobby, "href=\"/board/1\"")
    }

    @Test
    fun `invalid uploads are rejected with a message`() {
        assertContains(createQuizError(), "Bitte mindestens eine Board-Datei")
        assertContains(
            createQuizError(boardFile("Board_1.txt", "Kat"), MockMultipartFile("images", "../x.png", "image/png", byteArrayOf(1))),
            "Ungültige Bilddateinamen",
        )
        assertContains(createQuizError(boardFile("Board_1.txt", "Kat"), image("bild.svg")), "Nicht unterstützte Bildformate: bild.svg")
        assertContains(createQuizError(boardFile("Board_1.txt", "Kat", image = "fehlt.png")), "nicht hochgeladen: fehlt.png")
        assertContains(
            createQuizError(MockMultipartFile("boards", "Kaputt.txt", "text/plain", "category_1:\n    name: K\n    nope: x".toByteArray())),
            "Kaputt.txt, Zeile 3",
        )
    }

    @Test
    fun `categories need exactly six questions to be played`() {
        val fiveQuestions = String(boardFile("Board_1.txt", "Kat").bytes).substringBefore("question_6:")
        assertContains(
            createQuizError(MockMultipartFile("boards", "Board_1.txt", "text/plain", fiveQuestions.toByteArray())),
            "hat 5 Fragen, erwartet werden 6",
        )
    }

    @Test
    fun `boards are ordered by file name with numbers sorted naturally`() {
        val quiz = createQuiz(boardFile("Board_10.txt", "Zehn"), boardFile("Board_2.txt", "Zwei"))

        assertContains(page("/board/1", quiz.host), "Zwei")
        assertContains(page("/board/2", quiz.host), "Zehn")
    }

    @Test
    fun `creating a new quiz on the same device ends the previous one`() {
        val first = createQuiz()
        val second = mockMvc.multipart("/quiz") { file(boardFile("Board_1.txt", "Kat")); cookie(first.host) }
            .andReturn().response.getCookie(SessionCookies.HOST)!!
        hosts.add(second)

        assertContains(joinError(first.code, "Anna"), "Kein Quiz mit diesem Code gefunden.")
        mockMvc.get("/host") { cookie(first.host) }.andExpect { redirectedUrl("/") }
    }

    // Joining

    @Test
    fun `participants join with the code and are recognised on their device`() {
        val quiz = createQuiz()
        val anna = join(quiz, "Anna")

        assertContains(page("/play", anna), "Anna")
        mockMvc.get("/join") { cookie(anna) }.andExpect { redirectedUrl("/play") }
        assertContains(page("/host", quiz.host), "Anna")
    }

    @Test
    fun `joining is rejected for wrong codes, taken names, bad names and full quizzes`() {
        val quiz = createQuiz()
        val wrongCode = if (quiz.code == "999999") "111111" else "999999"

        assertContains(joinError(wrongCode, "Anna"), "Kein Quiz mit diesem Code gefunden.")
        join(quiz, "Anna")
        assertContains(joinError(quiz.code, "anna"), "ist in diesem Quiz schon vergeben")
        assertContains(joinError(quiz.code, " "), "zwischen 1 und 20 Zeichen")
        assertContains(joinError(quiz.code, "x".repeat(21)), "zwischen 1 und 20 Zeichen")
        join(quiz, "Ben")
        join(quiz, "Cem")
        assertContains(joinError(quiz.code, "Dana"), "Das Quiz ist voll.")
    }

    // Buzzing

    @Test
    fun `buzzers only work while a question is open, once per participant, in order`() {
        val quiz = createQuiz()
        val anna = join(quiz, "Anna")
        val ben = join(quiz, "Ben")

        assertContains(buzz(anna), "\"buzzed\":false")

        openQuestion(quiz, 3)
        assertContains(buzz(ben), "\"position\":1")
        assertContains(buzz(anna), "\"position\":2")
        assertContains(buzz(ben), "\"position\":1")

        page("/board/1/category/1/question/3/answer", quiz.host)
        assertContains(buzz(anna), "\"buzzingOpen\":false")
    }

    @Test
    fun `opening a question marks it as asked on the board`() {
        val quiz = createQuiz()
        assertFalse("disabled=\"disabled\"" in page("/board/1", quiz.host))

        openQuestion(quiz, 4)

        assertEquals(1, Regex("disabled=\"disabled\"").findAll(page("/board/1", quiz.host)).count())
    }

    @Test
    fun `question and answer pages render for every type`() {
        val quiz = createQuiz(boardFile("Board_1.txt", "Kat", image = "bild.png"), image("bild.png"))

        openQuestion(quiz, 1)
        assertContains(page("/board/1/category/1/question/1", quiz.host), "/images/bild.png")
        assertContains(page("/board/1/category/1/question/2", quiz.host), "Kat Frage 2")
        assertContains(page("/board/1/category/1/question/2/answer", quiz.host), "Kat Antwort 2")
    }

    @Test
    fun `lineup questions show their entries one by one, other types don't get the reveal button`() {
        val lineup = String(boardFile("Board_1.txt", "Kat").bytes)
            .replaceFirst("type: text\n    text: Kat Frage 1", "type: lineup\n    text: Erster, Zweiter ,Dritter")
        val quiz = createQuiz(MockMultipartFile("boards", "Board_1.txt", "text/plain", lineup.toByteArray()))

        val response = mockMvc.get("/board/1/category/1/question/1") { cookie(quiz.host) }.andReturn().response
        val html = response.contentAsString
        val entries = Regex("<p class=\"hidden\">([^<]*)</p>").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Erster", "Zweiter", "Dritter"), entries)
        assertContains(html, "id=\"reveal-button\"")

        val nonce = Regex("'nonce-([^']+)'").find(response.getHeader("Content-Security-Policy")!!)!!.groupValues[1]
        assertContains(html, "<script nonce=\"$nonce\">")

        assertFalse("reveal-button" in page("/board/1/category/1/question/2", quiz.host))
    }

    @Test
    fun `answers show text, image or both depending on their type`() {
        val board = String(boardFile("Board_1.txt", "Kat").bytes)
            .replaceFirst("type: text\n        text: Kat Antwort 1\n        img: null", "type: img\n        text: null\n        img: a.png")
            .replaceFirst("type: text\n        text: Kat Antwort 2\n        img: null", "type: textimage\n        text: Beides\n        img: b.png")
        val quiz = createQuiz(MockMultipartFile("boards", "Board_1.txt", "text/plain", board.toByteArray()), image("a.png"), image("b.png"))

        val imageOnly = page("/board/1/category/1/question/1/answer", quiz.host)
        assertContains(imageOnly, "/images/a.png")
        assertFalse("class=\"answer\"" in imageOnly)

        val both = page("/board/1/category/1/question/2/answer", quiz.host)
        assertContains(both, "/images/b.png")
        assertContains(both, "Beides")

        val textOnly = page("/board/1/category/1/question/3/answer", quiz.host)
        assertContains(textOnly, "Kat Antwort 3")
        assertFalse("answer-image" in textOnly)
    }

    @Test
    fun `non-numeric addresses are rejected`() {
        val quiz = createQuiz()
        mockMvc.get("/board/1/category/x/question/1") { cookie(quiz.host) }.andExpect { status { isBadRequest() } }
        mockMvc.post("/board/1/category/1/question/eins/open") { cookie(quiz.host) }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `board buttons link to their category and question, numbered from 1`() {
        val quiz = createQuiz(boardFile("Board_1.txt", "Eins", "Zwei"))
        val board = page("/board/1", quiz.host)

        assertContains(board, "action=\"/board/1/category/1/question/1/open\"")
        assertContains(board, "action=\"/board/1/category/2/question/6/open\"")

        mockMvc.post("/board/1/category/2/question/3/open") { cookie(quiz.host) }
            .andExpect { redirectedUrl("/board/1/category/2/question/3") }
        assertContains(page("/board/1/category/2/question/3", quiz.host), "Zwei Frage 3")
        assertContains(page("/board/1/category/2/question/3/answer", quiz.host), "Zwei Antwort 3")
    }

    @Test
    fun `addresses outside the board are not found`() {
        val quiz = createQuiz()

        listOf("/board/0", "/board/2", "/board/1/category/0/question/1", "/board/1/category/2/question/1",
            "/board/1/category/1/question/0", "/board/1/category/1/question/7", "/board/1/category/1/question/7/answer")
            .forEach { path -> mockMvc.get(path) { cookie(quiz.host) }.andExpect { status { isNotFound() } } }
        mockMvc.post("/board/1/category/1/question/7/open") { cookie(quiz.host) }.andExpect { status { isNotFound() } }
    }

    // Scoring

    @Test
    fun `without participants the answer leads back to the board, with them to scoring`() {
        val quiz = createQuiz()
        assertContains(page("/board/1/category/1/question/1/answer", quiz.host), "action=\"/board/1\"")

        join(quiz, "Anna")
        assertContains(page("/board/1/category/1/question/1/answer", quiz.host), "action=\"/board/1/category/1/question/1/score\"")
    }

    @Test
    fun `the host awards and deducts points, and can undo`() {
        val quiz = createQuiz()
        val anna = join(quiz, "Anna")
        join(quiz, "Ben")
        openQuestion(quiz, 3)
        buzz(anna)

        val rows = page("/board/1/category/1/question/3/score", quiz.host)
        assertTrue(rows.indexOf("Anna") < rows.indexOf("Ben"))

        score(quiz, 3, playerId = 1, outcome = "CORRECT")
        score(quiz, 3, playerId = 2, outcome = "WRONG")
        assertContains(buzz(anna), "\"score\":300")
        val totals = Regex("class=\"total\">(-?\\d+)").findAll(page("/board/1/category/1/question/3/score", quiz.host)).map { it.groupValues[1] }.toList()
        assertEquals(listOf("300", "-150"), totals)

        score(quiz, 3, playerId = 1, outcome = "CORRECT")
        assertContains(buzz(anna), "\"score\":0")
    }

    @Test
    fun `scoring an unknown participant changes nothing`() {
        val quiz = createQuiz()
        join(quiz, "Anna")
        score(quiz, 1, playerId = 99, outcome = "CORRECT")
        mockMvc.post("/board/1/category/1/question/1/score") { cookie(quiz.host); param("playerId", "1"); param("outcome", "MAYBE") }
            .andExpect { status { isBadRequest() } }
    }

    // Boards and scoreboard

    @Test
    fun `the last board leads to the scoreboard`() {
        val quiz = createQuiz(boardFile("Board_1.txt", "Eins"), boardFile("Board_2.txt", "Zwei"))

        assertContains(page("/board/1", quiz.host), "href=\"/board/2\"")
        assertContains(page("/board/2", quiz.host), "href=\"/host/scoreboard\"")
        mockMvc.get("/board/3") { cookie(quiz.host) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `the scoreboard shares places on ties and skips the next ones`() {
        val quiz = createQuiz()
        listOf("Anna", "Ben", "Cem").forEach { join(quiz, it) }
        score(quiz, 3, playerId = 1, outcome = "CORRECT")
        score(quiz, 3, playerId = 2, outcome = "CORRECT")

        val html = page("/host/scoreboard", quiz.host)
        val steps = Regex("place-(\\d)").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(listOf("1", "3"), steps)
        assertContains(html, "Cem")
    }

    // Ending and access

    @Test
    fun `ending the quiz removes it for host and participants`() {
        val quiz = createQuiz()
        val anna = join(quiz, "Anna")

        val response = mockMvc.post("/host/end") { cookie(quiz.host) }.andExpect { redirectedUrl("/") }.andReturn().response
        assertEquals(0, response.getCookie(SessionCookies.HOST)!!.maxAge)

        mockMvc.get("/host") { cookie(quiz.host) }.andExpect { redirectedUrl("/") }
        mockMvc.get("/play") { cookie(anna) }.andExpect { redirectedUrl("/join") }
        mockMvc.post("/play/buzz") { cookie(anna) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `participants and strangers can't open the host's pages`() {
        val quiz = createQuiz(boardFile("Board_1.txt", "Kat", image = "bild.png"), image("bild.png"))
        val anna = join(quiz, "Anna")

        listOf("/host", "/board/1", "/board/1/category/1/question/1", "/board/1/category/1/question/1/answer", "/board/1/category/1/question/1/score", "/host/scoreboard")
            .forEach { path -> mockMvc.get(path) { cookie(anna) }.andExpect { redirectedUrl("/") } }
        mockMvc.post("${q(1)}/open") { cookie(anna) }.andExpect { redirectedUrl("/") }
        mockMvc.get("/images/bild.png") { cookie(anna) }.andExpect { status { isNotFound() } }
        mockMvc.get("/images/bild.png").andExpect { status { isNotFound() } }
    }

    @Test
    fun `uploaded images are served to the host with a sandboxing policy`() {
        val quiz = createQuiz(boardFile("Board_1.txt", "Kat", image = "bild.png"), image("bild.png"))

        val response = mockMvc.get("/images/bild.png") { cookie(quiz.host) }.andExpect { status { isOk() } }.andReturn().response
        assertEquals("image/png", response.contentType)
        assertContains(response.getHeader("Content-Security-Policy")!!, "sandbox")
    }

    @Test
    fun `live update streams require the right cookie and start with the current state`() {
        val quiz = createQuiz()
        val anna = join(quiz, "Anna")

        mockMvc.get("/host/events").andExpect { status { isUnauthorized() } }
        mockMvc.get("/play/events").andExpect { status { isUnauthorized() } }

        val host = mockMvc.get("/host/events") { cookie(quiz.host) }.andExpect { request { asyncStarted() } }.andReturn()
        assertContains(host.response.contentAsString, "event:state")
        assertContains(host.response.contentAsString, "\"code\":\"${quiz.code}\"")

        val player = mockMvc.get("/play/events") { cookie(anna) }.andExpect { request { asyncStarted() } }.andReturn()
        assertContains(player.response.contentAsString, "\"name\":\"Anna\"")
    }

    @Test
    fun `cross-site requests can't end a quiz`() {
        val quiz = createQuiz()

        mockMvc.post("/host/end") { cookie(quiz.host); header("Sec-Fetch-Site", "cross-site") }
            .andExpect { status { isForbidden() } }

        page("/host", quiz.host)
        assertNotEquals("", quiz.code)
    }
}
