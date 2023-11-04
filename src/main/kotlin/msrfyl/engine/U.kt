package msrfyl.engine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

object U {

    val logger = LoggerFactory.getLogger("msrfyl.engine")!!

    fun toJsonString(any: Any): String = jacksonObjectMapper().writeValueAsString(any)
    fun <T> jsonReadValue(v: String, any: Class<T>): T = jacksonObjectMapper().readValue(v, any)

}

@Component
class AppContext : ApplicationContextAware {
    override fun setApplicationContext(context: ApplicationContext) {
        CONTEXT = context
    }

    companion object {
        private var CONTEXT: ApplicationContext? = null
        fun <T> getBean(clazz: Class<T>): T = CONTEXT!!.getBean(clazz)
    }
}