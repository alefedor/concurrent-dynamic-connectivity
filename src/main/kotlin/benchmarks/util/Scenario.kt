package benchmarks.util

import java.lang.RuntimeException

class Scenario(val nodes: Int, val threads: Int, val initialEdges: LongArray, val queries: Array<LongArray>)

enum class QueryType(val id: Int) {
    ADD_EDGE(1),
    REMOVE_EDGE(2),
    CONNECTED(3)
}

inline fun Long.type(): QueryType = when (this shr 48) {
    1L -> QueryType.ADD_EDGE
    2L -> QueryType.REMOVE_EDGE
    3L -> QueryType.CONNECTED
    else -> throw RuntimeException("Unknown query type")
}

inline fun Long.edgeToQuery(type: QueryType) = this or ((1L shl 48) * type.id)
