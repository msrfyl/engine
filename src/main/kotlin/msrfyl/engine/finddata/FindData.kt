package msrfyl.engine.finddata

import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.*
import msrfyl.engine.*
import msrfyl.engine.model.Criteria
import msrfyl.engine.model.FilterData
import msrfyl.engine.model.JoinObject
import msrfyl.engine.repository.UniversalRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.io.Serializable
import java.lang.reflect.Field
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class FindData<T, ID> private constructor(
    val universalRepository: UniversalRepository<T, ID>,
    val cls: Class<T>,
    val filterData: FilterData?,
    val pageable: Pageable,
    val search: String? = null,
    val columnSearch: Array<String>? = null
) {
    private val logger = U.logger
    private val formatDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val formatDatetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val formatTime = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val allFields = FindDataTool.getAllFields(cls)
    private var entityManager: EntityManager? = null
    private var joinMap: MutableList<JoinObject> = mutableListOf()

    companion object {
        fun fromJson(json: String): FilterData? {
            return try {
                U.jsonReadValue(json, FilterData::class.java)
            } catch (e: Exception) {
                U.logger.error("failed convert from json", e)
                null
            }
        }

        fun toJson(filterData: FilterData): String? {
            return try {
                U.toJsonString(filterData)
            } catch (e: Exception) {
                U.logger.error("failed convert to object", e)
                null
            }
        }

        fun filter(field: String, criteria: Criteria, value: String): FilterData = FilterData(field, criteria, value)

        fun or(vararg fd: FilterData): FilterData = FilterData(or = arrayOf(*fd))
        fun and(vararg fd: FilterData): FilterData = FilterData(and = arrayOf(*fd))

    }

    data class Builder<T, ID>(
        val universalRepository: UniversalRepository<T, ID>, val cls: Class<T>
    ) {

        private var jsonFilter: String? = null
        private var search: String? = null
        private var columnSearch: Array<String> = arrayOf()
        private var pageable: Pageable = Pageable.unpaged()
        private var joinMap: MutableList<JoinObject> = mutableListOf()

        fun addFilterJson(i: String) = apply { jsonFilter = i }
        fun setPageable(i: Pageable) = apply { pageable = i }
        fun setSearchKey(i: String) = apply { search = i }
        fun setColumnSearch(vararg i: String) = apply { columnSearch = arrayOf(*i) }

        private fun fd() = FindData(
            universalRepository = universalRepository, cls = cls, filterData = jsonFilter?.let { fromJson(it) },
            pageable = pageable, search = search, columnSearch = columnSearch
        )

        fun toPage(): Page<T> {
            val f = fd()
            f.joinMap = joinMap
            return f.findData()
        }

        fun getData(): MutableList<T> = fd().findData().content
        fun getPickupData(entityManager: EntityManager, select: Array<String>): List<Map<String, Any>> {
            val f = fd()
            f.entityManager = entityManager
            f.joinMap = joinMap
            return f.pickupData(select)
        }

        fun <Y> addJoin(name: String, clazz: Class<Y>) = apply {
            joinMap.add(JoinObject(name, clazz))
        }

    }

    fun findData(): Page<T> {
        val specification = Specification { root, _, criteriaBuilder ->
            val li = mutableListOf<Predicate>()
            filterData?.let { buildPredicate(root, criteriaBuilder, it)?.let { pr -> li.add(pr) } }
            search?.let {
                FindDataTool.buildPredicateSearch(
                    allFields, root, criteriaBuilder, it, columnSearch ?: arrayOf()
                )?.let { pr -> li.add(pr) }
            }
            criteriaBuilder.and(*li.toTypedArray())
        }

        return universalRepository.findAll(specification, pageable)
    }

    private fun buildPredicate(root: Root<T>, criteria: CriteriaBuilder, fd: FilterData): Predicate? {
        return try {
            when {
                fd.field != null && fd.criteria != null && fd.value != null -> {
                    var fieldRoot = allFields.firstOrNull { f -> f.name == fd.field }
                    var isJoin = false

                    val rootClass = if (fd.field.contains(".")) {
                        val fld = fd.field.split(".")
                        joinMap.firstOrNull { jo ->
                            jo.name == fld.first()
                        }?.let { join ->
                            isJoin = true
                            val fieldJoin = FindDataTool.getAllFields(join.clazz)
                            fieldRoot = fieldJoin.firstOrNull { fj ->
                                fj.name == fld.last()
                            }
                            root.join<Any, Any>(join.name, JoinType.LEFT)
                        } ?: root
                    } else root

                    fieldRoot?.let { fi ->
                        when (fd.criteria) {
                            Criteria.EQ -> if (fi.type.isEnum) {
                                criteria.equal(
                                    rootClass.get<Enum<*>>(fd.field),
                                    fi.type.enumConstants.first { any -> any.toString() == fd.value }
                                )
                            } else {
                                criteria.equal(
                                    if (isJoin) FindDataTool.toTypeJoinDataExpression(fi, rootClass, fd.field)
                                    else FindDataTool.toTypeDataExpression(fi, root, fd.field),
                                    when (fi.type) {
                                        Boolean::class.java -> fd.value.toBoolean()
                                        Int::class.java -> fd.value.toInt()
                                        Double::class.java -> fd.value.toDouble()
                                        LocalDateTime::class.java -> LocalDateTime.parse(fd.value, formatDatetime)
                                        LocalDate::class.java -> LocalDate.parse(fd.value, formatDate)
                                        LocalTime::class.java -> LocalTime.parse(fd.value, formatTime)
                                        else -> fd.value
                                    }
                                )
                            }

                            Criteria.NEQ -> if (fi.type.isEnum) {
                                criteria.notEqual(
                                    rootClass.get<Enum<*>>(fd.field),
                                    fi.type.enumConstants.first { any -> any.toString() == fd.value }
                                )
                            } else {
                                criteria.notEqual(
                                    if (isJoin) FindDataTool.toTypeJoinDataExpression(fi, rootClass, fd.field)
                                    else FindDataTool.toTypeDataExpression(fi, root, fd.field),
                                    when (fi.type) {
                                        Boolean::class.java -> fd.value.toBoolean()
                                        Int::class.java -> fd.value.toInt()
                                        Double::class.java -> fd.value.toDouble()
                                        LocalDateTime::class.java -> LocalDateTime.parse(fd.value, formatDatetime)
                                        LocalDate::class.java -> LocalDate.parse(fd.value, formatDate)
                                        LocalTime::class.java -> LocalTime.parse(fd.value, formatTime)
                                        Enum::class.java -> fi.type.enumConstants.first { any -> any.toString() == fd.value }
                                        else -> fd.value
                                    }
                                )
                            }

                            Criteria.GT -> when (fi.type) {
                                LocalDateTime::class.java -> criteria.greaterThan(
                                    rootClass.get<LocalDateTime>(fd.field),
                                    LocalDateTime.parse(fd.value, formatDatetime)
                                )

                                LocalDate::class.java -> criteria.greaterThan(
                                    rootClass.get<LocalDate>(fd.field),
                                    LocalDate.parse(fd.value, formatDate)
                                )

                                LocalTime::class.java -> criteria.greaterThan(
                                    rootClass.get<LocalTime>(fd.field),
                                    LocalTime.parse(fd.value, formatTime)
                                )

                                Double::class.java -> criteria.greaterThan(
                                    rootClass.get<Double>(fd.field),
                                    fd.value.toDouble()
                                )

                                else -> criteria.greaterThan(rootClass.get<Int>(fd.field), fd.value.toInt())
                            }

                            Criteria.LT -> when (fi.type) {
                                LocalDateTime::class.java -> criteria.lessThan(
                                    rootClass.get<LocalDateTime>(fd.field),
                                    LocalDateTime.parse(fd.value, formatDatetime)
                                )

                                LocalDate::class.java -> criteria.lessThan(
                                    rootClass.get<LocalDate>(fd.field),
                                    LocalDate.parse(fd.value, formatDate)
                                )

                                LocalTime::class.java -> criteria.lessThan(
                                    rootClass.get<LocalTime>(fd.field),
                                    LocalTime.parse(fd.value, formatTime)
                                )

                                Double::class.java -> criteria.lessThan(
                                    rootClass.get<Double>(fd.field),
                                    fd.value.toDouble()
                                )

                                else -> criteria.lessThan(rootClass.get<Int>(fd.field), fd.value.toInt())
                            }

                            Criteria.GTE -> when (fi.type) {
                                LocalDateTime::class.java -> criteria.greaterThanOrEqualTo(
                                    rootClass.get<LocalDateTime>(fd.field),
                                    LocalDateTime.parse(fd.value, formatDatetime)
                                )

                                LocalDate::class.java -> criteria.greaterThanOrEqualTo(
                                    rootClass.get<LocalDate>(fd.field),
                                    LocalDate.parse(fd.value, formatDate)
                                )

                                LocalTime::class.java -> criteria.greaterThanOrEqualTo(
                                    rootClass.get<LocalTime>(fd.field),
                                    LocalTime.parse(fd.value, formatTime)
                                )

                                Double::class.java -> criteria.greaterThanOrEqualTo(
                                    rootClass.get<Double>(fd.field),
                                    fd.value.toDouble()
                                )

                                else -> criteria.greaterThanOrEqualTo(rootClass.get<Int>(fd.field), fd.value.toInt())
                            }

                            Criteria.LTE -> when (fi.type) {
                                LocalDateTime::class.java -> criteria.lessThanOrEqualTo(
                                    rootClass.get<LocalDateTime>(fd.field),
                                    LocalDateTime.parse(fd.value, formatDatetime)
                                )

                                LocalDate::class.java -> criteria.lessThanOrEqualTo(
                                    rootClass.get<LocalDate>(fd.field),
                                    LocalDate.parse(fd.value, formatDate)
                                )

                                LocalTime::class.java -> criteria.lessThanOrEqualTo(
                                    rootClass.get<LocalTime>(fd.field),
                                    LocalTime.parse(fd.value, formatTime)
                                )

                                Double::class.java -> criteria.lessThanOrEqualTo(
                                    rootClass.get<Double>(fd.field),
                                    fd.value.toDouble()
                                )

                                else -> criteria.lessThanOrEqualTo(rootClass.get<Int>(fd.field), fd.value.toInt())
                            }

                            Criteria.LIKE -> criteria.like(
                                criteria.lower(
                                    (if (isJoin) FindDataTool.toTypeJoinDataExpression(fi, rootClass, fd.field)
                                    else FindDataTool.toTypeDataExpression(fi, root, fd.field)).`as`(String::class.java)
                                ), fd.value.lowercase()
                            )

                            Criteria.ISNULL -> criteria.isNull(
                                if (isJoin) FindDataTool.toTypeJoinDataExpression(fi, rootClass, fd.field)
                                else FindDataTool.toTypeDataExpression(fi, root, fd.field)
                            )

                            Criteria.ISNOTNULL -> criteria.isNotNull(
                                if (isJoin) FindDataTool.toTypeJoinDataExpression(fi, rootClass, fd.field)
                                else FindDataTool.toTypeDataExpression(fi, root, fd.field)
                            )

                            Criteria.IN -> (if (isJoin) FindDataTool.toTypeJoinDataExpression(fi, rootClass, fd.field)
                            else FindDataTool.toTypeDataExpression(fi, root, fd.field)).`in`(fd.field.split(";"))

                            Criteria.NOTIN -> criteria.not(
                                (if (isJoin) FindDataTool.toTypeJoinDataExpression(fi, rootClass, fd.field)
                                else FindDataTool.toTypeDataExpression(fi, root, fd.field)).`in`(fd.field.split(";"))
                            )
                        }
                    }
                }

                fd.and != null -> criteria.and(*fd.and.map { m -> buildPredicate(root, criteria, m) }.toTypedArray())
                fd.or != null -> criteria.or(*fd.or.map { m -> buildPredicate(root, criteria, m) }.toTypedArray())
                else -> null
            }
        } catch (e: Exception) {
            logger.error("failed build predicate filter", e)
            null
        }
    }

    private fun pickupData(select: Array<String>): List<Map<String, Any>> {
        return entityManager?.let { em ->
            try {
                val criteriaBuilder = em.criteriaBuilder
                val criteriaQuery = criteriaBuilder.createQuery(Object::class.java)
                val itemRoot = criteriaQuery.from(cls)
                var predicate: Predicate? = null

                filterData?.let { fd ->
                    buildPredicate(itemRoot, criteriaBuilder, fd)?.let { p ->
                        predicate = p
                    }
                }

                val fieldPaths: MutableList<Path<out Serializable>> = mutableListOf()
                select.forEach {
                    if (it.contains(".")) {
                        val column = it.split(".")
                        joinMap.firstOrNull { jo ->
                            jo.name == column.first()
                        }?.let { join ->
                            val joined: Join<*, *> = itemRoot.join<Any, Any>(join.name, JoinType.LEFT)
                            FindDataTool.getAllFields(join.clazz).firstOrNull { fi -> fi.name == column.last() }
                                ?.let { field ->
                                    fieldPaths.add(
                                        when (field.type) {
                                            Boolean::class.java -> joined.get<Boolean>(field.name)
                                            Int::class.java -> joined.get<Int>(field.name)
                                            Double::class.java -> joined.get<Double>(field.name)
                                            LocalDateTime::class.java -> joined.get<LocalDateTime>(field.name)
                                            LocalDate::class.java -> joined.get<LocalDate>(field.name)
                                            LocalTime::class.java -> joined.get<LocalTime>(field.name)
                                            else -> joined.get<String>(field.name)
                                        }
                                    )
                                }
                        }
                    } else {
                        FindDataTool.getAllFields(cls).firstOrNull { fi -> fi.name == it }?.let { field ->
                            fieldPaths.add(FindDataTool.toTypeDataExpression(field, itemRoot, field.name))
                        }
                    }
                }

                val queryData = em.createQuery(
                    predicate?.let { criteriaQuery.multiselect(*fieldPaths.toTypedArray()).where(it) }
                        ?: criteriaQuery.multiselect(*fieldPaths.toTypedArray())
                ).resultList

                queryData.map { m ->
                    m as Array<out Any>
                    mapOf(*select.mapIndexed { index, s ->
                        try {
                            Pair(s, m[index])
                        } catch (e: Exception) {
                            Pair(s, "")
                        }
                    }.toTypedArray())
                }
            } catch (e: Exception) {
                logger.error("failed get pickup data", e)
                mutableListOf()
            }
        } ?: mutableListOf()

    }

}

object FindDataTool {
    fun toTypeDataExpression(fi: Field, root: Root<*>, fieldStr: String): Path<out Serializable> {
        return when (fi.type) {
            Boolean::class.java -> root.get<Boolean>(fieldStr)
            String::class.java -> root.get<String>(fieldStr)
            Int::class.java -> root.get<Int>(fieldStr)
            Double::class.java -> root.get<Double>(fieldStr)
            LocalDateTime::class.java -> root.get<LocalDateTime>(fieldStr)
            LocalDate::class.java -> root.get<LocalDate>(fieldStr)
            LocalTime::class.java -> root.get<LocalTime>(fieldStr)
            else -> root.get(fieldStr)
        }
    }

    fun toTypeJoinDataExpression(fi: Field, root: From<out Any, out Any>, fieldStr: String): Path<out Serializable> {
        return when (fi.type) {
            Boolean::class.java -> root.get<Boolean>(fieldStr.split(".").last())
            String::class.java -> root.get<String>(fieldStr.split(".").last())
            Int::class.java -> root.get<Int>(fieldStr.split(".").last())
            Double::class.java -> root.get<Double>(fieldStr.split(".").last())
            LocalDateTime::class.java -> root.get<LocalDateTime>(fieldStr.split(".").last())
            LocalDate::class.java -> root.get<LocalDate>(fieldStr.split(".").last())
            LocalTime::class.java -> root.get<LocalTime>(fieldStr.split(".").last())
            else -> root.get(fieldStr.split(".").last())
        }
    }

    fun buildPredicateSearch(
        allFields: MutableList<Field>, root: Root<*>, criteria: CriteriaBuilder,
        search: String, searchColumn: Array<String>
    ): Predicate? {
        val availableColumn = if (searchColumn.isEmpty()) allFields
        else allFields.filter { f -> searchColumn.any { a -> a == f.name } }
        return try {
            criteria.or(
                *availableColumn.map { m ->
                    criteria.like(
                        criteria.lower(toTypeDataExpression(m, root, m.name).`as`(String::class.java)),
                        "%${search.lowercase()}%"
                    )
                }.toTypedArray()
            )
        } catch (e: Exception) {
            U.logger.error("failed build predicate search", e)
            null
        }
    }

    fun <T> getAllFields(clazz: Class<T>): MutableList<Field> {
        val fields: MutableList<Field> = mutableListOf()
        fields.addAll(clazz.superclass.declaredFields)
        fields.addAll(clazz.declaredFields)
        return fields
            .filter { f ->
                !f.annotations.any { a ->
                    a.toString().contains("JsonIgnore")
                            || a.toString().contains("Transient")
                            || a.toString().contains("OneToMany")
                }
            }.toMutableList()
    }

}