package connectivity.sequential.tree

import connectivity.sequential.OperationType
import connectivity.sequential.SlowConnectivity
import connectivity.sequential.TreeConnectivityScenarioGenerator
import org.junit.Assert.*
import org.junit.Test

class EulerTourTreeTest {
    @Test
    fun stress() {
        val iterations = 1000000
        val nodes = 6
        val scenarioSize = 20

        repeat(iterations) {
            val scenario = TreeConnectivityScenarioGenerator.generate(nodes, scenarioSize)
            val slowConnectivity = SlowConnectivity(nodes)
            val connectivity = SequentialEulerTourTreeImpl(nodes)
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
                    OperationType.SAME_COMPONENTS -> assertEquals(
                        slowConnectivity.sameComponent(operation.args[0], operation.args[1]),
                        connectivity.sameComponent(operation.args[0], operation.args[1])
                    )
                }
            }
        }
    }

    @Test
    fun testSimple() {
        val connectivity = SequentialEulerTourTreeImpl(5)
        assertFalse(connectivity.sameComponent(0, 1))
        connectivity.addEdge(0, 1)
        assertTrue(connectivity.sameComponent(0, 1))
        connectivity.addEdge(1, 2)
        assertTrue(connectivity.sameComponent(0, 2))
        connectivity.removeEdge(2, 1)
        assertFalse(connectivity.sameComponent(2, 0))
        assertTrue(connectivity.sameComponent(0, 1))
    }
}