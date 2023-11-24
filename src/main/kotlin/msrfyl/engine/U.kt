package msrfyl.engine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

class U {

    companion object {
        val logger = LoggerFactory.getLogger("msrfyl.engine")!!

        fun toJsonString(any: Any): String = jacksonObjectMapper().writeValueAsString(any)
        fun <T> jsonReadValue(v: String, any: Class<T>): T = jacksonObjectMapper().readValue(v, any)
        fun patternFormat(pattern: String, locale: Locale = Locale.ENGLISH): DateTimeFormatter =
            DateTimeFormatter.ofPattern(pattern, locale)
    }

    data class UString(val v: String) {
        fun toLocalDate(pattern: String = "yyyy-MM-dd"): LocalDate = LocalDate.parse(v, patternFormat(pattern))
        fun toLocalDatetime(pattern: String = "yyyy-MM-dd HH:mm:ss"): LocalDateTime =
            LocalDateTime.parse(v, patternFormat(pattern))

        fun toLocalTime(pattern: String = "HH:mm:ss"): LocalTime = LocalTime.parse(v, patternFormat(pattern))
    }

    data class ULocalDate(val v: LocalDate) {
        fun format(pattern: String = "yyyy-MM-dd"): String = v.format(patternFormat(pattern))
        fun toStartOfDay(): LocalDateTime = LocalDateTime.of(v, LocalTime.of(0, 0, 0))
        fun toEndOfDay(): LocalDateTime = LocalDateTime.of(v, LocalTime.of(23, 59, 59))
    }

    data class ULocalDatetime(val v: LocalDateTime) {
        fun format(pattern: String = "yyyy-MM-dd HH:mm:ss"): String = v.format(patternFormat(pattern))
    }

    data class ULocalTime(val v: LocalTime) {
        fun format(pattern: String = "HH:mm:ss"): String = v.format(patternFormat(pattern))
    }

}