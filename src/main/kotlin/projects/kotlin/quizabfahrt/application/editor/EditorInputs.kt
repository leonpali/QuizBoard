package projects.kotlin.quizabfahrt.application.editor

import projects.kotlin.quizabfahrt.model.EAnswerTypes
import projects.kotlin.quizabfahrt.model.EQuestionTypes

data class QuestionInput(val type: EQuestionTypes, val text: String?, val img: String?, val points: String) {

    fun isType(name: String) = type.name == name

    companion object {
        fun of(question: QuestionDraft) = QuestionInput(question.type, question.text, question.img, question.points.toString())
    }
}

data class AnswerInput(val type: EAnswerTypes, val text: String?, val img: String?) {

    fun isType(name: String) = type.name == name

    companion object {
        fun of(question: QuestionDraft) = AnswerInput(question.answerType, question.answerText, question.answerImg)
    }
}
