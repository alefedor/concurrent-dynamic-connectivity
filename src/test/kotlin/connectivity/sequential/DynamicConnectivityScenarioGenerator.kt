package connectivity.sequential

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class DynamicConnectivityScenarioGenerator(private val type: ScenarioType) {
    private val rndGen = Random(0)

    fun generate(nodes: Int, scenarioSize: Int): List<Operation> {
        val connectivity = SlowConnectivity(nodes)
        val scenario = mutableListOf<Operation>()
        val edges = mutableListOf<Pair<Int, Int>>()

        while (scenario.size < scenarioSize) {
            val rnd = (0 until 5).random(rndGen)

            val opType = when (rnd) {
                in 0..1 -> OperationType.ADD_EDGE
                2 -> OperationType.REMOVE_EDGE
                else -> OperationType.CONNECTED
            }

            when (opType) {
                OperationType.ADD_EDGE -> {
                    val u = (0 until nodes).random(rndGen)
                    val v = (0 until nodes).random(rndGen)

                    val edge = Pair(min(u, v), max(u, v))

                    val canAddEdge = when (type) {
                        ScenarioType.TREE_CONNECTIVITY -> !connectivity.sameComponent(u, v)
                        ScenarioType.GENERAL_CONNECTIVITY -> u != v && edges.none { it == edge}
                    }
                    if (canAddEdge) {
                        edges += edge
                        scenario += Operation(
                            OperationType.ADD_EDGE,
                            listOf(u, v)
                        )
                        connectivity.addEdge(u, v)
                    }
                }
                OperationType.REMOVE_EDGE -> {
                    if (edges.isNotEmpty()) {
                        val edge = edges.random()
                        edges.removeIf { it == edge }
                        scenario += Operation(
                            OperationType.REMOVE_EDGE,
                            listOf(edge.first, edge.second)
                        )
                        connectivity.removeEdge(edge.first, edge.second)
                    }
                }
                OperationType.CONNECTED -> {
                    scenario += Operation(
                        OperationType.CONNECTED,
                        listOf((0 until nodes).random(rndGen), (0 until nodes).random(rndGen))
                    )
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
    CONNECTED
}

enum class ScenarioType {
    GENERAL_CONNECTIVITY,
    TREE_CONNECTIVITY
}