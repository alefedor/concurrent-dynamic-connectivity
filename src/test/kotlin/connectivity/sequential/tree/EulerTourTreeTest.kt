package connectivity.sequential.tree

import connectivity.concurrent.general.major.MajorConcurrentEulerTourTree
import connectivity.concurrent.tree.ConcurrentEulerTourTree
import connectivity.sequential.OperationType
import connectivity.sequential.SlowConnectivity
import connectivity.sequential.DynamicConnectivityScenarioGenerator
import connectivity.sequential.ScenarioType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

enum class TreeDynamicConnectivityConstructor(val construct: (size: Int) -> TreeDynamicConnectivity) {
    SequentialEulerTourTree(::SequentialEulerTourTree),
    ConcurrentEulerTourTree(::ConcurrentEulerTourTree),
    MajorConcurrentEulerTourTree(::MajorConcurrentEulerTourTree)
}

@RunWith(Parameterized::class)
class EulerTourTreeTest(private val dcp: TreeDynamicConnectivityConstructor) {
    @Test
    fun stress() {
        val iterations = 5000000
        val nodes = 8
        val scenarioSize = 25
        val scenarioGenerator = DynamicConnectivityScenarioGenerator(ScenarioType.TREE_CONNECTIVITY)

        repeat(iterations) {
            val scenario = scenarioGenerator.generate(nodes, scenarioSize)
            val slowConnectivity = SlowConnectivity(nodes)
            val connectivity = dcp.construct(nodes)
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
        val connectivity = dcp.construct(5)
        assertFalse(connectivity.connected(0, 1))
        connectivity.addEdge(0, 1)
        assertTrue(connectivity.connected(0, 1))
        connectivity.addEdge(1, 2)
        assertTrue(connectivity.connected(0, 2))
        connectivity.removeEdge(2, 1)
        assertFalse(connectivity.connected(2, 0))
        assertTrue(connectivity.connected(0, 1))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun dcpConstructors() = TreeDynamicConnectivityConstructor.values()
    }
}