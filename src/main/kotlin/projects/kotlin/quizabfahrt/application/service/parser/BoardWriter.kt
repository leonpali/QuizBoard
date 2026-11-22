package projects.kotlin.quizabfahrt.application.service.parser

import projects.kotlin.quizabfahrt.model.Category
import projects.kotlin.quizabfahrt.model.Content

object BoardWriter {

    fun write(categories: List<Category>): String = buildString {
        categories.forEachIndexed { c, category ->
            appendLine("category_${c + 1}:")
            appendLine("    name: ${category.name}")
            appendLine("    questions:")
            category.questions.forEachIndexed { q, question ->
                appendLine("        question_${q + 1}:")
                appendContent(question.content, "            ")
                appendLine("            points: ${question.points}")
                appendLine("            answer:")
                appendContent(question.answer, "                ")
            }
        }
    }

    private fun StringBuilder.appendContent(content: Content<*>, indent: String) {
        appendLine("${indent}type: ${content.type.key}")
        content.values.forEach { (field, value) -> appendLine("$indent${field.key}: $value") }
    }
}
