package projects.kotlin.quizabfahrt.model

data class Category(
    val id: Int,
    val name: String,
    val questions: List<Question>
)
