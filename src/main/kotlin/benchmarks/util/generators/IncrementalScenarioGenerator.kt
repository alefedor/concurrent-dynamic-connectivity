package benchmarks.util.generators

import benchmarks.util.Graph
import benchmarks.util.QueryType
import benchmarks.util.Scenario
import benchmarks.util.edgeToQuery
import kotlin.random.Random

class IncrementalScenarioGenerator {
    fun generate(graph: Graph, threads: Int): Scenario {
        val queries = Array(threads) {
            if (it == 0) {
                LongArray(graph.edges.size) {
                    graph.edges[it].edgeToQuery(QueryType.ADD_EDGE)
                }
            } else {
                LongArray(0)
            }
        }
        return Scenario(graph.nodes, threads, LongArray(0), queries)
    }
}