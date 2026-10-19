package projects.kotlin.quizabfahrt.application.service

import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.model.Question

@Service
class QuestionService(private val quizStore: QuizStore) {

    fun changeAskedStatus(boardId: Int, questionId: Int) {
        quizStore
            .getBoard(boardId)
            .categories[calculateCategoryIndex(questionId)]
            .questions[calculateQuestionIndex(questionId)]
            .asked = true
    }

    fun getQuestion(boardId: Int, questionId: Int): Question {
        return quizStore
            .getBoard(boardId)
            .categories[calculateCategoryIndex(questionId)]
            .questions[calculateQuestionIndex(questionId)]
    }

    private fun calculateCategoryIndex(questionId: Int): Int {
        return questionId / 6
    }

    private fun calculateQuestionIndex(questionId: Int): Int {
        return questionId % 6
    }
}