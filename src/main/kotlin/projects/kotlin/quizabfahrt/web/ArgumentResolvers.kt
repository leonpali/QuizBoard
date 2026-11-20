package projects.kotlin.quizabfahrt.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.util.WebUtils
import projects.kotlin.quizabfahrt.application.editor.BoardEditorService
import projects.kotlin.quizabfahrt.application.session.QuizSessionService
import projects.kotlin.quizabfahrt.model.QuestionRef

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class HostSession

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class EditorDraft

@Component
class HostSessionResolver(private val sessions: QuizSessionService) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) = parameter.hasParameterAnnotation(HostSession::class.java)

    override fun resolveArgument(
        parameter: MethodParameter, mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest, binderFactory: WebDataBinderFactory?,
    ): Any? {
        val token = getCookie(webRequest, SessionCookies.HOST)
        return if (parameter.isOptional) sessions.findByHostToken(token) else sessions.requireHost(token)
    }
}

@Component
class EditorDraftResolver(private val editor: BoardEditorService) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) = parameter.hasParameterAnnotation(EditorDraft::class.java)

    override fun resolveArgument(
        parameter: MethodParameter, mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest, binderFactory: WebDataBinderFactory?,
    ): Any? {
        val token = getCookie(webRequest, SessionCookies.EDITOR)
        return if (parameter.isOptional) editor.findDraft(token) else editor.requireDraft(token)
    }
}

@Component
class QuestionRefResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == QuestionRef::class.java

    override fun resolveArgument(
        parameter: MethodParameter, mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest, binderFactory: WebDataBinderFactory?,
    ): Any {
        @Suppress("UNCHECKED_CAST")
        val variables = webRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            as Map<String, String>? ?: emptyMap()
        fun number(name: String) = variables[name]?.toIntOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        return QuestionRef.fromPath(number("board"), number("category"), number("question"))
    }
}

private fun getCookie(webRequest: NativeWebRequest, name: String): String? =
    webRequest.getNativeRequest(HttpServletRequest::class.java)?.let { WebUtils.getCookie(it, name)?.value }

@Configuration
class WebConfig(
    private val hostSessionResolver: HostSessionResolver,
    private val editorDraftResolver: EditorDraftResolver,
    private val questionRefResolver: QuestionRefResolver,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.addAll(listOf(hostSessionResolver, editorDraftResolver, questionRefResolver))
    }
}
