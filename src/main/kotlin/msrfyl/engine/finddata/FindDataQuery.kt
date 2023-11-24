package msrfyl.engine.finddata

import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import msrfyl.engine.model.Criteria
import msrfyl.engine.model.FilterData
import msrfyl.engine.model.JoinObjectQuery
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class FindDataQuery<T> private constructor(
    private val entityManager: EntityManager,
    private val clazz: Class<T>,
    private var sort: String? = null,
    private var search: String? = null,
    private var columnSearch: Array<String> = arrayOf(),
    private var pageable: Pageable = Pageable.unpaged(),
    private var joinMap: MutableList<JoinObjectQuery> = mutableListOf(),
    private var filter: FilterData? = null
) {

    private val className = clazz.name.split(".").last()
    private val aliasClassName = className.lowercase()
    private val allFields = FindDataTool.getAllFields(clazz)
    private val formatDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val formatDatetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val formatTime = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val sorted = sort?.let {
        val s = it.split(",")
        val fi = if (s.first().contains(".")) s.first() else "${aliasClassName}.${s.first()}"
        " \n ORDER BY $fi ${s.last().uppercase()} "
    }


    data class Builder<T>(val entityManager: EntityManager, val clazz: Class<T>) {
        private var sort: String? = null
        private var search: String? = null
        private var columnSearch: Array<String> = arrayOf()
        private var pageable: Pageable = Pageable.unpaged()
        private var joinMap: MutableList<JoinObjectQuery> = mutableListOf()
        private var filter: FilterData? = null

        fun sort(i: String) = apply { sort = i }
        fun search(i: String) = apply { search = i }
        fun setColumnSearch(vararg i: String) = apply { columnSearch = arrayOf(*i) }
        fun setPageable(i: Pageable) = apply { pageable = i }
        fun <Y> addJoin(name: String, clazz: Class<Y>, from: String, to: String) =
            apply { joinMap.add(JoinObjectQuery(name, clazz, from, to)) }

        fun filter(fi: FilterData) = apply { filter = fi }

        fun pageData(): Page<T> {
            return FindDataQuery(entityManager, clazz, sort, search, columnSearch, pageable, joinMap, filter).findData()
        }

        fun listData(): List<T> {
            return FindDataQuery(entityManager, clazz, sort, search, columnSearch, Pageable.unpaged(), joinMap, filter)
                .findData().content
        }

    }

    fun findData(): Page<T> {
        val whereList: MutableList<String> = mutableListOf()
        filter?.let { buildPredicate(it)?.let { pre -> whereList.add(pre) } }

        val joinQuery = if (joinMap.isEmpty()) " " else {
            joinMap.joinToString(" ") {
                " \n LEFT JOIN  ${it.nameClazz} ${it.aliasClazzName} ON ${it.aliasClazzName}.${it.from} = ${aliasClassName}.${it.to} "
            }
        }

        search?.let {
            val searchList: MutableList<String> = mutableListOf()
            buildPredicateSearch(clazz, it, columnSearch)?.let { s -> searchList.add(s) }
            joinMap.forEach { j ->
                buildPredicateSearch(j.clazz, it, columnSearch, j.aliasClazzName)?.let { s -> searchList.add(s) }
            }
            if (searchList.isNotEmpty()) {
                whereList.add(" \n ( ${searchList.joinToString(" OR ")} ) ")
            }
        }

        val fromQuery = " \n FROM $className $aliasClassName " +
                "$joinQuery " +
                "${if (whereList.isEmpty()) " " else " \n WHERE ${whereList.joinToString(" AND ")}"} "

        val queryCountMaxData = "SELECT COUNT($aliasClassName) $fromQuery "
        val maxData = try {
            entityManager.createQuery(queryCountMaxData).singleResult as Long
        } catch (e: Exception) {
            Int.MAX_VALUE.toLong()
        }

        val query = "SELECT $aliasClassName $fromQuery ${sorted ?: ""} "
        val typeQuery: TypedQuery<T> = entityManager.createQuery(query, clazz)
        return PageImpl(typeQuery.resultList, pageable, maxData)
    }

    private fun buildPredicate(fd: FilterData): String? {
        return try {
            when {
                fd.field != null && fd.criteria != null && fd.value != null -> {
                    var fieldRoot = allFields.firstOrNull { f -> f.name == fd.field }
                    val rootClass = if (fd.field.contains(".")) {
                        val fld = fd.field.split(".")
                        joinMap.firstOrNull { jo ->
                            jo.name == fld.first()
                        }?.let { join ->
                            val fieldJoin = FindDataTool.getAllFields(join.clazz)
                            fieldRoot = fieldJoin.firstOrNull { fj -> fj.name == fld.last() }
                            join.clazz.name.split(".").last().lowercase()
                        } ?: aliasClassName
                    } else aliasClassName

                    fieldRoot?.let { fi ->
                        when (fd.criteria) {
                            Criteria.EQ -> if (fi.type.isEnum) {
                                " ${rootClass}.${fi.name} = ${fi.type.enumConstants.first { any -> any.toString() == fd.value }} "
                            } else {
                                " ${rootClass}.${fi.name} = ${
                                    when (fi.type) {
                                        Boolean::class.java -> " '${fd.value}' "
                                        Int::class.java -> " ${fd.value} "
                                        Double::class.java -> " ${fd.value} "
                                        LocalDateTime::class.java -> " '${
                                            LocalDateTime.parse(
                                                fd.value,
                                                formatDatetime
                                            )
                                        }' "

                                        LocalDate::class.java -> " '${LocalDate.parse(fd.value, formatDate)}' "
                                        LocalTime::class.java -> " '${LocalTime.parse(fd.value, formatTime)}' "
                                        else -> " '${fd.value}' "
                                    }
                                } "
                            }

                            Criteria.NEQ -> if (fi.type.isEnum) {
                                " ${rootClass}.${fi.name} != ${fi.type.enumConstants.first { any -> any.toString() == fd.value }} "
                            } else {
                                " ${rootClass}.${fi.name} != ${
                                    when (fi.type) {
                                        Boolean::class.java -> " '${fd.value}' "
                                        Int::class.java -> " ${fd.value} "
                                        Double::class.java -> " ${fd.value} "
                                        LocalDateTime::class.java -> " '${
                                            LocalDateTime.parse(
                                                fd.value,
                                                formatDatetime
                                            )
                                        }' "

                                        LocalDate::class.java -> " '${LocalDate.parse(fd.value, formatDate)}' "
                                        LocalTime::class.java -> " '${LocalTime.parse(fd.value, formatTime)}' "
                                        else -> " '${fd.value}' "
                                    }
                                } "
                            }

                            Criteria.GT -> when (fi.type) {
                                LocalDateTime::class.java -> " ${rootClass}.${fi.name} > '${
                                    LocalDateTime.parse(
                                        fd.value,
                                        formatDatetime
                                    )
                                }' "

                                LocalDate::class.java -> " ${rootClass}.${fi.name} > '${
                                    LocalDate.parse(
                                        fd.value,
                                        formatDate
                                    )
                                }' "

                                LocalTime::class.java -> " ${rootClass}.${fi.name} > '${
                                    LocalTime.parse(
                                        fd.value,
                                        formatTime
                                    )
                                }' "

                                Double::class.java -> " ${rootClass}.${fi.name} > ${fd.value} "
                                else -> " ${rootClass}.${fi.name} > ${fd.value} "
                            }

                            Criteria.LT -> when (fi.type) {
                                LocalDateTime::class.java -> " ${rootClass}.${fi.name} < '${
                                    LocalDateTime.parse(
                                        fd.value,
                                        formatDatetime
                                    )
                                }' "

                                LocalDate::class.java -> " ${rootClass}.${fi.name} < '${
                                    LocalDate.parse(
                                        fd.value,
                                        formatDate
                                    )
                                }' "

                                LocalTime::class.java -> " ${rootClass}.${fi.name} < '${
                                    LocalTime.parse(
                                        fd.value,
                                        formatTime
                                    )
                                }' "

                                Double::class.java -> " ${rootClass}.${fi.name} < ${fd.value} "
                                else -> " ${rootClass}.${fi.name} < ${fd.value} "
                            }

                            Criteria.GTE -> when (fi.type) {
                                LocalDateTime::class.java -> " ${rootClass}.${fi.name} >= '${
                                    LocalDateTime.parse(
                                        fd.value,
                                        formatDatetime
                                    )
                                }' "

                                LocalDate::class.java -> " ${rootClass}.${fi.name} >= '${
                                    LocalDate.parse(
                                        fd.value,
                                        formatDate
                                    )
                                }' "

                                LocalTime::class.java -> " ${rootClass}.${fi.name} >= '${
                                    LocalTime.parse(
                                        fd.value,
                                        formatTime
                                    )
                                }' "

                                Double::class.java -> " ${rootClass}.${fi.name} >= ${fd.value} "
                                else -> " ${rootClass}.${fi.name} >= ${fd.value} "
                            }

                            Criteria.LTE -> when (fi.type) {
                                LocalDateTime::class.java -> " ${rootClass}.${fi.name} <= '${
                                    LocalDateTime.parse(
                                        fd.value,
                                        formatDatetime
                                    )
                                }' "

                                LocalDate::class.java -> " ${rootClass}.${fi.name} <= '${
                                    LocalDate.parse(
                                        fd.value,
                                        formatDate
                                    )
                                }' "

                                LocalTime::class.java -> " ${rootClass}.${fi.name} <= '${
                                    LocalTime.parse(
                                        fd.value,
                                        formatTime
                                    )
                                }' "

                                Double::class.java -> " ${rootClass}.${fi.name} <= ${fd.value} "
                                else -> " ${rootClass}.${fi.name} <= ${fd.value} "
                            }

                            Criteria.LIKE -> " ${rootClass}.${fi.name} LIKE ${fd.value} "
                            Criteria.ISNULL -> " ${rootClass}.${fi.name} IS NULL "
                            Criteria.ISNOTNULL -> " ${rootClass}.${fi.name} IS NOT NULL "
                            Criteria.IN -> " (${
                                fd.value.split(";").joinToString(" OR ") { m -> " ${rootClass}.${fi.name} = '$m' " }
                            }) "

                            Criteria.NOTIN -> " (${
                                fd.value.split(";").joinToString(" AND ") { m -> " ${rootClass}.${fi.name} != '$m' " }
                            }) "

                            else -> ""
                        }
                    }
                }

                fd.and != null -> " ( ${fd.and.map { m -> buildPredicate(m) }.joinToString(" AND ")} ) "
                fd.or != null -> " ( ${fd.or.map { m -> buildPredicate(m) }.joinToString(" OR ")} ) "
                else -> ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildPredicateSearch(
        clazz: Class<*>, search: String, searchColumn: Array<String>, aliasRoot: String = ""
    ): String? {
        val allFields = FindDataTool.getAllFields(clazz).filterNot { it.type.isEnum }
        val availableColumn = if (searchColumn.isEmpty()) allFields
        else allFields.filter { f ->
            searchColumn.any { a ->
                a == (if (aliasRoot == "") "${
                    clazz.name.split(".").last().lowercase()
                }.${f.name}" else "${aliasRoot}.${f.name}")
            }
        }
        return try {
            " ( ${
                availableColumn.joinToString(" OR ") { m ->
                    " ${
                        if (aliasRoot == "") "${clazz.name.split(".").last().lowercase()}.${m.name}"
                        else "${aliasRoot}.${m.name}"
                    } LIKE '%$search%' "
                }
            } ) "
        } catch (e: Exception) {
            null
        }
    }

}