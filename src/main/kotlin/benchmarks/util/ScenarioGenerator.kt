package benchmarks.util

import java.lang.RuntimeException
import kotlin.random.Random

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

class ScenarioGenerator {
    private val rnd = Random(343)

    fun generate(graph: Graph, threads: Int, sizePerThread: Int, updateWeight: Int, readWeight: Int): Scenario {
        val initialEdgesNumber = graph.edges.size / 2
        val initialEdges = graph.edges.copyOfRange(0, initialEdgesNumber)

        val edgesPerThread = initialEdgesNumber / threads

        val queries = Array(threads) {thread ->
            val candidatesToAdd: MutableList<Long> = MutableList(edgesPerThread) { graph.edges[initialEdgesNumber + thread * edgesPerThread + it] }
            val candidatesToRemove: MutableList<Long> = MutableList(edgesPerThread) { graph.edges[thread * edgesPerThread + it] }

            LongArray(sizePerThread) {
                var type: QueryType

                while (true) {
                    type = randomQueryType(updateWeight, readWeight)
                    if (type == QueryType.ADD_EDGE && candidatesToAdd.isEmpty()) continue
                    if (type == QueryType.REMOVE_EDGE && candidatesToRemove.isEmpty()) continue
                    break
                }

                when (type) {
                    QueryType.CONNECTED -> {
                        val edge = graph.edges.random(rnd) // random edge from all graph
                        edge
                    }
                    QueryType.ADD_EDGE -> {
                        val edge = candidatesToAdd.pop(candidatesToAdd.indices.random(rnd))
                        candidatesToRemove.add(edge)
                        edge
                    }
                    QueryType.REMOVE_EDGE -> {
                        val edge = candidatesToRemove.pop(candidatesToRemove.indices.random(rnd))
                        candidatesToAdd.add(edge)
                        edge
                    }
                }.edgeToQuery(type)
            }
        }

        return Scenario(graph.nodes, threads, initialEdges, queries)
    }

    private fun randomQueryType(updateWeight: Int, readWeight: Int): QueryType {
        val r = (0 until (updateWeight + readWeight)).random(rnd)
        return if (r < readWeight) {
            QueryType.CONNECTED
        } else {
            if (rnd.nextBoolean())
                QueryType.ADD_EDGE
            else
                QueryType.REMOVE_EDGE
        }
    }

    private fun <T> MutableList<T>.pop(i: Int): T {
        val result = this[i]
        this[i] = this.last()
        this.removeAt(this.lastIndex)
        return result
    }
}