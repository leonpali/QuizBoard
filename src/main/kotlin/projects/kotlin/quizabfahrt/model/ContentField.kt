package projects.kotlin.quizabfahrt.model

enum class ContentField(
    val key: String,
    val label: String,
    val maxLength: Int,
    val extensions: List<String> = emptyList(),
    val isMultiline: Boolean = false,
) {
    TEXT("text", "Text", maxLength = 1000, isMultiline = true),
    IMAGE("img", "Bild", maxLength = 100, extensions = listOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp"));

    val isFile: Boolean get() = extensions.isNotEmpty()

    companion object {
        val FILE_EXTENSIONS: List<String> = entries.flatMap { it.extensions }
    }
}
