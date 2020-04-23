package benchmarks.util

import kotlin.random.Random

class IncrementalScenarioGenerator {
    private val rnd = Random(647)
    fun generate(graph: Graph, threads: Int): Scenario {
        val edgesPerThread = graph.edges.size / threads
        val queries = Array(threads) { thread ->
            LongArray(edgesPerThread) {
                graph.edges[thread * edgesPerThread + it].edgeToQuery(QueryType.ADD_EDGE)
            }
        }
        return Scenario(graph.nodes, threads, LongArray(0), queries)
    }
}