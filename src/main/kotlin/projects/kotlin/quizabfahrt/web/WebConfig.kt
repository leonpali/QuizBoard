package projects.kotlin.quizabfahrt.web

import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import projects.kotlin.quizabfahrt.application.service.QuizService

@Configuration
class WebConfig(private val quizService: QuizService) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/images/**")
            .addResourceLocations("file:${quizService.imageDir}/", "classpath:/static/images/")
            .setCacheControl(CacheControl.noCache())
    }
}
