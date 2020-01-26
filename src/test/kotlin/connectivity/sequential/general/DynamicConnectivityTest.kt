package connectivity.sequential.general

import connectivity.sequential.DynamicConnectivityScenarioGenerator
import connectivity.sequential.OperationType
import connectivity.sequential.ScenarioType
import connectivity.sequential.SlowConnectivity
import org.junit.Assert.*
import org.junit.Test

class DynamicConnectivityTest {
    @Test
    fun stress() {
        val iterations = 10000000
        val nodes = 6
        val scenarioSize = 20
        val scenarioGenerator = DynamicConnectivityScenarioGenerator(ScenarioType.GENERAL_CONNECTIVITY)

        repeat(iterations) {
            val scenario = scenarioGenerator.generate(nodes, scenarioSize)
            val slowConnectivity = SlowConnectivity(nodes)
            val connectivity = SequentialDynamicConnectivity(nodes)
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
                    OperationType.CONNECTED -> {
                        if (slowConnectivity.sameComponent(operation.args[0], operation.args[1]) != connectivity.connected(operation.args[0], operation.args[1])) {
                            scenario.forEach {
                                print(
                                    when (it.type) {
                                        OperationType.ADD_EDGE -> "add("
                                        OperationType.REMOVE_EDGE -> "remove("
                                        OperationType.CONNECTED -> "same("
                                    }
                                )
                                println("${it.args[0]}, ${it.args[1]})")
                            }
                            println()
                            operation.let {
                                print(
                                    when (it.type) {
                                        OperationType.ADD_EDGE -> "add("
                                        OperationType.REMOVE_EDGE -> "remove("
                                        OperationType.CONNECTED -> "same("
                                    }
                                )
                                println("${it.args[0]}, ${it.args[1]})")
                                println("${slowConnectivity.sameComponent(operation.args[0], operation.args[1])} : ${connectivity.connected(operation.args[0], operation.args[1])}")
                            }
                            fail()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun simple() {
        val connectivity = SequentialDynamicConnectivity(5)
        assertFalse(connectivity.connected(0, 1))
        connectivity.addEdge(0, 1)
        assertTrue(connectivity.connected(0, 1))
        connectivity.addEdge(1, 2)
        assertTrue(connectivity.connected(0, 2))
        connectivity.addEdge(0, 2)
        assertTrue(connectivity.connected(0, 2))
        connectivity.removeEdge(2, 1)
        assertTrue(connectivity.connected(2, 0))
        connectivity.removeEdge(0, 2)
        assertFalse(connectivity.connected(2, 0))
        assertTrue(connectivity.connected(0, 1))
    }

    @Test
    fun failing() {
        val connectivity = SequentialDynamicConnectivity(6)
        connectivity.addEdge(4, 5)
        connectivity.addEdge(0, 1)
        connectivity.addEdge(5, 3)
        connectivity.addEdge(1, 2)
        connectivity.addEdge(4, 0)
        connectivity.addEdge(2, 0)
        connectivity.removeEdge(0, 4)
        connectivity.addEdge(0, 5)
        connectivity.removeEdge(0, 1)
        assertTrue(connectivity.connected(4, 2))
    }
}