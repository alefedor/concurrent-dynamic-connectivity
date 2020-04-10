package connectivity.concurrent

import connectivity.sequential.DynamicConnectivityScenarioGenerator
import connectivity.sequential.Operation
import connectivity.sequential.OperationType
import connectivity.sequential.ScenarioType
import org.jetbrains.kotlinx.lincheck.Actor
import org.jetbrains.kotlinx.lincheck.CTestConfiguration
import org.jetbrains.kotlinx.lincheck.CTestStructure
import org.jetbrains.kotlinx.lincheck.execution.ExecutionGenerator
import org.jetbrains.kotlinx.lincheck.execution.ExecutionScenario
import java.lang.reflect.Method
import java.util.*
import kotlin.math.max


abstract class DynamicConnectivitySingleWriterExecutionGenerator(testConfiguration: CTestConfiguration, testStructure: CTestStructure, scenarioType: ScenarioType) :
    ExecutionGenerator(testConfiguration, testStructure) {
    private val n: Int
    private val addEdgeMethod: Method
    private val removeEdgeMethod: Method
    private val connectedMethod: Method
    private val scenarioGenerator = DynamicConnectivityScenarioGenerator(scenarioType)
    private val random = Random()

    init {
        var m = 0
        val iterations = 600
        repeat(iterations) {
            m = max(m, testStructure.actorGenerators[0].generate().arguments.map { Integer::class.java.cast(it).toInt() }.max()!!)
        }
        n = m + 1

        addEdgeMethod = testStructure.actorGenerators.first {  it.generate().method.name == "addEdge" }.generate().method
        removeEdgeMethod = testStructure.actorGenerators.first {  it.generate().method.name == "removeEdge" }.generate().method
        connectedMethod = testStructure.actorGenerators.first {  it.generate().method.name == "connected" }.generate().method
    }

    override fun nextExecution(): ExecutionScenario { // Create init execution part
        val actorsBefore = testConfiguration.actorsBefore
        val actorsAfter = testConfiguration.actorsAfter
        val actorsPerThread = testConfiguration.actorsPerThread
        val threads = testConfiguration.threads

        fun createActor(operation: Operation) = when (operation.type) {
            OperationType.ADD_EDGE -> addEdgeActor(operation.args[0], operation.args[1])
            OperationType.REMOVE_EDGE -> removeEdgeActor(operation.args[0], operation.args[1])
            OperationType.CONNECTED -> connectedActor(operation.args[0], operation.args[1])
        }

        val writerExecution = scenarioGenerator.generate(n, actorsBefore + actorsPerThread + actorsAfter).map(::createActor)

        val initExecution = writerExecution.subList(0, actorsBefore)
        val postExecution = writerExecution.subList(actorsBefore + actorsPerThread, writerExecution.size)
        val parallelExecution = MutableList(threads - 1) {
            List(actorsPerThread) { Operation(OperationType.CONNECTED, listOf(random.nextInt(n), random.nextInt(n))) }.map(::createActor)
        }

        parallelExecution.add(writerExecution.subList(actorsBefore, actorsBefore + actorsPerThread)) // the only writer thread

        return ExecutionScenario(initExecution, parallelExecution, postExecution)
    }

    private fun addEdgeActor(u: Int, v: Int) = Actor(addEdgeMethod, listOf(u, v), emptyList())
    private fun removeEdgeActor(u: Int, v: Int) = Actor(removeEdgeMethod, listOf(u, v), emptyList())
    private fun connectedActor(u: Int, v: Int) = Actor(connectedMethod, listOf(u, v), emptyList())
}

class TreeDynamicConnectivitySingleWriterExecutionGenerator(testConfiguration: CTestConfiguration, testStructure: CTestStructure) :
    DynamicConnectivitySingleWriterExecutionGenerator(testConfiguration, testStructure, ScenarioType.TREE_CONNECTIVITY)

class GeneralDynamicConnectivitySingleWriterExecutionGenerator(testConfiguration: CTestConfiguration, testStructure: CTestStructure) :
    DynamicConnectivitySingleWriterExecutionGenerator(testConfiguration, testStructure, ScenarioType.GENERAL_CONNECTIVITY)