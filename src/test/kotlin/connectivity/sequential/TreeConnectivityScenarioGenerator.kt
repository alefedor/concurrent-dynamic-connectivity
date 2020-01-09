package connectivity.sequential

object TreeConnectivityScenarioGenerator {
    fun generate(nodes: Int, scenarioSize: Int): List<Operation> {
        val connectivity = SlowConnectivity(nodes)
        val scenario = mutableListOf<Operation>()
        val edges = mutableListOf<Pair<Int, Int>>()

        while (scenario.size < scenarioSize) {
            val opType = OperationType.values().random()

            when (opType) {
                OperationType.ADD_EDGE -> {
                    val u = (0 until nodes).random()
                    val v = (0 until nodes).random()

                    if (!connectivity.sameComponent(u, v)) {
                        edges += u to v
                        scenario += Operation(OperationType.ADD_EDGE, listOf(u, v))
                        connectivity.addEdge(u, v)
                    }
                }
                OperationType.REMOVE_EDGE -> {
                    if (edges.isNotEmpty()) {
                        val edge = edges.random()
                        edges.removeIf { it == edge }
                        scenario += Operation(OperationType.REMOVE_EDGE, listOf(edge.first, edge.second))
                        connectivity.removeEdge(edge.first, edge.second)
                    }
                }
                OperationType.SAME_COMPONENTS -> {
                    scenario += Operation(OperationType.SAME_COMPONENTS, listOf((0 until nodes).random(), (0 until nodes).random()))
                }
            }
        }

        return scenario
    }
}

class Operation(val type: OperationType, val args: List<Int>)

enum class OperationType {
    ADD_EDGE,
    REMOVE_EDGE,
    SAME_COMPONENTS
}