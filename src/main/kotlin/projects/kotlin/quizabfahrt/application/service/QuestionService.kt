package projects.kotlin.quizabfahrt.application.service

import org.springframework.stereotype.Service
import projects.kotlin.quizabfahrt.application.service.parser.CompilationEngine

@Service
class QuestionService {

    fun changeAskedStatus(boardId: Int, categoryIndex: Int, questionId: Int) {
        CompilationEngine.getBoard(boardId).categories[categoryIndex].questions[questionId % 5].asked = true
    }
}