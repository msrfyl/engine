package msrfyl.engine.temporary

import java.time.LocalDateTime

@Suppress("UNCHECKED_CAST")
class TemporaryData<T> private constructor(
    private val name: String, private var data: T, private val getData: (T) -> T
) {
    private var lastUpdate: LocalDateTime = LocalDateTime.now()
    fun clear() {
        mapProcessing[name] = false
    }

    fun load(): T {
        mapProcessing[name]?.let {
            if (!it) {
                data = getData(data)
                lastUpdate = LocalDateTime.now()
                mapProcessing[name] = true
            }
        }
        return data
    }

    companion object {
        private val mapProcessing = mutableMapOf<String, Boolean>()
        private val mapBuffer = mutableMapOf<String, TemporaryData<*>>()
        fun <T> register(name: String, data: T, getData: (T) -> T): TemporaryData<T> {
            mapProcessing[name] = false
            mapBuffer[name] = TemporaryData(name, data, getData)
            @Suppress("UNCHECKED_CAST")
            return mapBuffer[name] as TemporaryData<T>
        }

    }

}