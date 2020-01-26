package connectivity.sequential.tree

import connectivity.sequential.OperationType
import connectivity.sequential.SlowConnectivity
import connectivity.sequential.DynamicConnectivityScenarioGenerator
import connectivity.sequential.ScenarioType
import org.junit.Assert.*
import org.junit.Test

class EulerTourTreeTest {
    @Test
    fun stress() {
        val iterations = 10000000
        val nodes = 6
        val scenarioSize = 20
        val scenarioGenerator = DynamicConnectivityScenarioGenerator(ScenarioType.TREE_CONNECTIVITY)

        repeat(iterations) {
            val scenario = scenarioGenerator.generate(nodes, scenarioSize)
            val slowConnectivity = SlowConnectivity(nodes)
            val connectivity = SequentialEulerTourTree(nodes)
            for (operation in scenario) {
                when (operation.type) {
                    OperationType.ADD_EDGE -> {
                        slowConnectivity.addEdge(operation.args[0], operation.args[1])
                        connectivity.addEdge(operation.args[0], operation.args[1])
                    }
                    OperationType.REMOVE_EDGE -> {
                        slowConnectivity.removeEdge(operation.args[0], operation.args[1])
                        connectivity.removeEdge(operation.args[0], operation.args[1])
                    }
                    OperationType.CONNECTED -> assertEquals(
                        slowConnectivity.sameComponent(operation.args[0], operation.args[1]),
                        connectivity.connected(operation.args[0], operation.args[1])
                    )
                }
            }
        }
    }

    @Test
    fun simple() {
        val connectivity = SequentialEulerTourTree(5)
        assertFalse(connectivity.connected(0, 1))
        connectivity.addEdge(0, 1)
        assertTrue(connectivity.connected(0, 1))
        connectivity.addEdge(1, 2)
        assertTrue(connectivity.connected(0, 2))
        connectivity.removeEdge(2, 1)
        assertFalse(connectivity.connected(2, 0))
        assertTrue(connectivity.connected(0, 1))
    }
}