package benchmarks.util.generators

import benchmarks.util.*
import kotlin.random.*

class FullyRandomScenarioGenerator {
    private val rnd = Random(343)

    fun generate(graph: Graph, threads: Int, sizePerThread: Int, updateWeight: Int, readWeight: Int, makeOverhead: Boolean, initialWeight: Int): Scenario {
        val initialEdgesNumber = (graph.edges.size / (1 + initialWeight)) * initialWeight
        val initialEdges = graph.edges.copyOfRange(0, initialEdgesNumber)

        val queries = Array(threads) {thread ->
            LongArray(sizePerThread * (if (makeOverhead) OVERHEAD_RATIO else 1)) {
                var type: QueryType
                type = randomQueryType(updateWeight, readWeight)

                randomEdge(graph).edgeToQuery(type)
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

    private fun randomEdge(graph: Graph): Long {
        return graph.edges.random(rnd)
    }
}