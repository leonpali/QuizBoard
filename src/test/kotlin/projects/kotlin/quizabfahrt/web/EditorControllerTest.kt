package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.Cookie
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
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class EditorControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val template = javaClass.getResource("/static/downloads/Board_Vorlage.txt")!!.readBytes()

    companion object {
        private val ipCounter = AtomicInteger()
    }

    // Every test gets its own client address, so the per-network draft limit doesn't interfere
    private val ip = RequestPostProcessor { it.remoteAddr = "10.0.0.${ipCounter.incrementAndGet()}"; it }

    private fun newDraft(): Cookie =
        mockMvc.post("/editor/new") { with(ip) }.andReturn().response.getCookie(SessionCookies.EDITOR)!!

    private fun page(path: String, draft: Cookie): String =
        mockMvc.get(path) { cookie(draft) }.andReturn().response.contentAsString

    private fun addCategory(draft: Cookie, name: String) {
        mockMvc.post("/editor/categories") { cookie(draft); param("name", name) }
            .andExpect { redirectedUrl("/editor/board") }
    }

    @Test
    fun `the start page links to the editor`() {
        assertContains(mockMvc.get("/").andReturn().response.contentAsString, "href=\"/editor\"")
        mockMvc.get("/editor").andExpect { status { isOk() } }
    }

    @Test
    fun `editor pages without a draft lead back to the editor start`() {
        mockMvc.get("/editor/board").andExpect { redirectedUrl("/editor") }
        mockMvc.get("/editor/categories/0") { cookie(Cookie(SessionCookies.EDITOR, "unknown")) }
            .andExpect { redirectedUrl("/editor") }
        mockMvc.post("/editor/categories") { param("name", "X") }.andExpect { redirectedUrl("/editor") }
    }

    @Test
    fun `a board can be built, edited and downloaded`() {
        val draft = newDraft()
        addCategory(draft, "Geografie")
        repeat(2) {
            mockMvc.post("/editor/categories/0/questions") { cookie(draft) }.andExpect { redirectedUrl("/editor/categories/0") }
        }

        mockMvc.post("/editor/categories/0/questions/0/question") {
            cookie(draft); param("type", "IMAGE"); param("text", ""); param("img", "koeln.jpg"); param("points", "250")
        }.andExpect { redirectedUrl("/editor/categories/0") }
        mockMvc.post("/editor/categories/0/questions/0/answer") {
            cookie(draft); param("type", "TEXTIMAGE"); param("text", "Kölner Dom"); param("img", "dom.jpg")
        }.andExpect { redirectedUrl("/editor/categories/0") }

        val response = mockMvc.post("/editor/download") { cookie(draft); param("fileName", "Erdkunde") }
            .andExpect { status { isOk() } }.andReturn().response
        val content = response.getContentAsString(Charsets.UTF_8)

        assertContains(response.getHeader("Content-Disposition")!!, "filename=\"Erdkunde.txt\"")
        assertContains(content, "name: Geografie")
        assertContains(content, "img: koeln.jpg")
        assertContains(content, "points: 250")
        assertContains(content, "type: textimage")
        assertContains(content, "text: Kölner Dom")
        assertEquals(0, response.getCookie(SessionCookies.EDITOR)!!.maxAge)
        mockMvc.get("/editor/board") { cookie(draft) }.andExpect { redirectedUrl("/editor") }
    }

    @Test
    fun `invalid input shows the form again with an error and the entered values`() {
        val draft = newDraft()
        addCategory(draft, "K")
        mockMvc.post("/editor/categories/0/questions") { cookie(draft) }

        val html = mockMvc.post("/editor/categories/0/questions/0/question") {
            cookie(draft); param("type", "IMAGE"); param("text", "Mein Text"); param("img", ""); param("points", "250")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertContains(html, "Eine Bildfrage braucht einen Bildnamen.")
        assertContains(html, "value=\"IMAGE\" selected")
        assertContains(html, "Mein Text")
        assertContains(page("/editor/categories/0/questions/0/question", draft), "value=\"TEXT\" selected")
    }

    @Test
    fun `categories and questions can be reordered`() {
        val draft = newDraft()
        listOf("A", "B", "C").forEach { addCategory(draft, it) }

        mockMvc.post("/editor/categories/2/move") { cookie(draft); param("direction", "UP") }
            .andExpect { redirectedUrl("/editor/board") }

        val names = Regex("class=\"item-title\">([^<]*)").findAll(page("/editor/board", draft)).map { it.groupValues[1] }.toList()
        assertEquals(listOf("A", "C", "B"), names)

        repeat(2) { mockMvc.post("/editor/categories/0/questions") { cookie(draft) } }
        mockMvc.post("/editor/categories/0/questions/1/move") { cookie(draft); param("direction", "UP") }
            .andExpect { redirectedUrl("/editor/categories/0") }
        val points = Regex("<span>(\\d+)</span> Punkte").findAll(page("/editor/categories/0", draft)).map { it.groupValues[1] }.toList()
        assertEquals(listOf("200", "100"), points)
    }

    @Test
    fun `an unknown move direction is rejected`() {
        val draft = newDraft()
        addCategory(draft, "A")
        mockMvc.post("/editor/categories/0/move") { cookie(draft); param("direction", "SIDEWAYS") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `links to deleted categories and questions lead back to the overview`() {
        val draft = newDraft()
        mockMvc.get("/editor/categories/3") { cookie(draft) }.andExpect { redirectedUrl("/editor/board") }
        mockMvc.get("/editor/categories/-1/questions/0/answer") { cookie(draft) }.andExpect { redirectedUrl("/editor/board") }
        mockMvc.post("/editor/categories/0/delete") { cookie(draft) }.andExpect { redirectedUrl("/editor/board") }
    }

    @Test
    fun `a board file can be uploaded into the editor`() {
        val response = mockMvc.multipart("/editor/upload") {
            file(MockMultipartFile("board", "Board_Vorlage.txt", "text/plain", template)); with(ip)
        }.andExpect { redirectedUrl("/editor/board") }.andReturn().response

        val html = page("/editor/board", response.getCookie(SessionCookies.EDITOR)!!)
        assertContains(html, "Beispielkategorie")
        assertContains(html, "6 von 6 Fragen")
    }

    @Test
    fun `invalid and oversized uploads show an error`() {
        val invalid = mockMvc.multipart("/editor/upload") {
            file(MockMultipartFile("board", "kaputt.txt", "text/plain", "category_1:\n    nope: x".toByteArray())); with(ip)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertContains(invalid, "Fehler in Board-Datei")

        val tooLarge = mockMvc.multipart("/editor/upload") {
            file(MockMultipartFile("board", "gross.txt", "text/plain", ByteArray(2_000_000) { 'a'.code.toByte() })); with(ip)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertContains(tooLarge, "Die Datei ist zu groß.")
    }

    @Test
    fun `posts from other sites are rejected and change nothing`() {
        val draft = newDraft()
        addCategory(draft, "Bleibt")

        mockMvc.post("/editor/categories/0/delete") { cookie(draft); header("Sec-Fetch-Site", "same-site") }
            .andExpect { status { isForbidden() } }
        mockMvc.post("/editor/download") { cookie(draft); header("Origin", "https://evil.example") }
            .andExpect { status { isForbidden() } }

        assertContains(page("/editor/board", draft), "Bleibt")
    }

    @Test
    fun `user input is escaped in the page`() {
        val draft = newDraft()
        addCategory(draft, "<script>alert(1)</script>")

        val html = page("/editor/board", draft)
        assertContains(html, "&lt;script&gt;alert(1)&lt;/script&gt;")
        assertFalse("<script>alert(1)" in html)
    }

    @Test
    fun `editor scripts carry the nonce from the security headers`() {
        val draft = newDraft()
        val response = mockMvc.get("/editor/board") { cookie(draft) }.andReturn().response

        val nonce = Regex("'nonce-([^']+)'").find(response.getHeader("Content-Security-Policy")!!)!!.groupValues[1]
        val scriptNonces = Regex("<script[^>]*nonce=\"([^\"]*)\"").findAll(response.contentAsString).map { it.groupValues[1] }.toList()

        assertTrue(scriptNonces.isNotEmpty())
        assertTrue(scriptNonces.all { it == nonce })
    }

    @Test
    fun `the download file name can't inject headers`() {
        val draft = newDraft()
        val response = mockMvc.post("/editor/download") { cookie(draft); param("fileName", "x\r\nSet-Cookie: evil=1") }
            .andReturn().response

        assertContains(response.getHeader("Content-Disposition")!!, "filename=\"Board.txt\"")
        assertTrue(response.getHeaders("Set-Cookie").none { "evil" in it })
    }
}
