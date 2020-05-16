package benchmarks.util.generators

import benchmarks.util.*

class DecrementalScenarioGenerator {
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
        return Scenario(graph.nodes, threads, graph.edges, queries)
    }
}