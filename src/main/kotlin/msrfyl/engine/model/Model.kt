package msrfyl.engine.model

enum class Criteria {
    EQ, // EQUAL
    NEQ, // NOT EQUAL
    LIKE,
    GT, // GREATER THAN
    LT, // LESS THAN
    GTE, // GREATER THAN EQUAL
    LTE, // LESS THAN EQUAL
    ISNULL,
    ISNOTNULL,
    IN,
    NOTIN
}

class FilterData(
    val field: String? = null,
    val criteria: Criteria? = null,
    val value: String? = null,
    val or: Array<FilterData>? = null,
    val and: Array<FilterData>? = null
)

class JoinObject(val name: String, val clazz: Class<*>)

class JoinObjectQuery(val name: String, val clazz: Class<*>, val from: String = "", val to: String = "") {
    val nameClazz = clazz.name.split(".").last()
    val aliasClazzName = nameClazz.lowercase()
}
