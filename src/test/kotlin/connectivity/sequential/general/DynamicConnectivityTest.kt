package connectivity.sequential.general

import connectivity.concurrent.general.*
import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.sequential.DynamicConnectivityScenarioGenerator
import connectivity.sequential.OperationType
import connectivity.sequential.ScenarioType
import connectivity.sequential.SlowConnectivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

enum class GeneralDynamicConnectivityConstructor(val construct: (size: Int) -> DynamicConnectivity) {
    /*SequentialDynamicConnectivity(::SequentialDynamicConnectivity),
    CoarseGrainedLockingDynamicConnectivity(::CoarseGrainedLockingDynamicConnectivity),
    ImprovedCoarseGrainedLockingDynamicConnectivity(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDynamicConnectivity(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    FineGrainedLockingDynamicConnectivity(::FineGrainedLockingDynamicConnectivity),
    SFineGrainedLockingDynamicConnectivity(::SFineGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteFairLockingDynamicConnectivity(::CoarseGrainedReadWriteFairLockingDynamicConnectivity),
    FineGrainedFairLockingDynamicConnectivity(::FineGrainedFairLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    ImprovedFineGrainedLockingDynamicConnectivity(::ImprovedFineGrainedLockingDynamicConnectivity),*/
    MajorDynamicConnectivity(::MajorDynamicConnectivity)
}

@RunWith(Parameterized::class)
class DynamicConnectivityTest(private val dcp: GeneralDynamicConnectivityConstructor) {
    @Test
    fun stress7() {
        val iterations = 1000000
        val nodes = 7
        val scenarioSize = 25
        stress(iterations, nodes, scenarioSize)
    }

    @Test
    fun stress9() {
        val iterations = 200000
        val nodes = 9
        val scenarioSize = 30
        stress(iterations, nodes, scenarioSize)
    }

    @Test
    fun stress11() {
        val iterations = 200000
        val nodes = 11
        val scenarioSize = 30
        stress(iterations, nodes, scenarioSize)
    }


    fun stress(iterations: Int, nodes: Int, scenarioSize: Int) {
        val scenarioGenerator = DynamicConnectivityScenarioGenerator(ScenarioType.GENERAL_CONNECTIVITY)

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
        val connectivity = dcp.construct(5)
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

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun dcpConstructors() = GeneralDynamicConnectivityConstructor.values()
    }
}