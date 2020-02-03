package benchmarks.util

import connectivity.sequential.general.DynamicConnectivity

class ScenarioExecutor(val scenario: Scenario, dcpConstructor: (Int) -> DynamicConnectivity) {
    private val dcp = dcpConstructor(scenario.nodes)

    private val threads: Array<Thread>

    init {
        for (edge in scenario.initialEdges)
            dcp.addEdge(edge.from(), edge.to())

        threads = Array(scenario.threads) { threadId ->
            Thread {
                var cnt = 0
                val queries = scenario.queries[threadId]
                for (query in queries) {
                    when (query.type()) {
                        QueryType.CONNECTED -> {
                            dcp.connected(query.from(), query.to())
                        }
                        QueryType.ADD_EDGE -> {
                            dcp.addEdge(query.from(), query.to())
                        }
                        QueryType.REMOVE_EDGE -> {
                            dcp.removeEdge(query.from(), query.to())
                        }
                    }
                }
            }
        }
    }

    fun run() {
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}