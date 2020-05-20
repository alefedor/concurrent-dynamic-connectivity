package benchmarks.util.generators

import benchmarks.util.*
import kotlin.random.Random

class DecrementalScenarioGenerator {
    private val rnd = Random(534)
    private val incrementalScenarioGenerator =
        IncrementalScenarioGenerator()

    fun generate(graph: Graph, threads: Int): Scenario {
        val queries = incrementalScenarioGenerator.generate(graph, threads).queries
        for (threadQueries in queries)
            for (i in threadQueries.indices)
                threadQueries[i] = bidirectionalEdge(
                    threadQueries[i].from(),
                    threadQueries[i].to()
                ).edgeToQuery(QueryType.REMOVE_EDGE)
        return Scenario(graph.nodes, threads, graph.edges.toList().shuffled(rnd).toLongArray(), queries)
    }
}