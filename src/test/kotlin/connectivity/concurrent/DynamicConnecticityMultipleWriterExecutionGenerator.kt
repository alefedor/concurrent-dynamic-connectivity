package connectivity.concurrent

import benchmarks.util.*
import benchmarks.util.generators.RandomScenarioGenerator
import org.jetbrains.kotlinx.lincheck.Actor
import org.jetbrains.kotlinx.lincheck.CTestConfiguration
import org.jetbrains.kotlinx.lincheck.CTestStructure
import org.jetbrains.kotlinx.lincheck.execution.ExecutionGenerator
import org.jetbrains.kotlinx.lincheck.execution.ExecutionScenario
import java.lang.reflect.Method
import kotlin.math.max
import kotlin.random.Random


// important: ignores actorsAfter and actorsAfter
class GeneralDynamicConnectivityMultipleWriterExecutionGenerator(testConfiguration: CTestConfiguration, testStructure: CTestStructure) :
    ExecutionGenerator(testConfiguration, testStructure) {
    private val n: Int
    private val addEdgeMethod: Method
    private val removeEdgeMethod: Method
    private val connectedMethod: Method
    private val scenarioGenerator = RandomScenarioGenerator()
    private val random = Random(65413)

    init {
        var m = 0
        val iterations = 600
        repeat(iterations) {
            m = max(m, testStructure.actorGenerators[0].generate(0).arguments.map { Integer::class.java.cast(it).toInt() }.max()!!)
        }
        n = m + 1

        addEdgeMethod = testStructure.actorGenerators.first {  it.generate(0).method.name == "addEdge" }.generate(0).method
        removeEdgeMethod = testStructure.actorGenerators.first {  it.generate(0).method.name == "removeEdge" }.generate(0).method
        connectedMethod = testStructure.actorGenerators.first {  it.generate(0).method.name == "connected" }.generate(0).method
    }

    override fun nextExecution(): ExecutionScenario {
        val actorsBefore = testConfiguration.actorsBefore
        val actorsAfter = testConfiguration.actorsAfter
        val actorsPerThread = testConfiguration.actorsPerThread
        val threads = testConfiguration.threads

        fun createActor(op: Long) = when (op.type()) {
            QueryType.ADD_EDGE -> addEdgeActor(op.to(), op.from())
            QueryType.REMOVE_EDGE -> removeEdgeActor(op.to(), op.from())
            QueryType.CONNECTED -> connectedActor(op.to(), op.from())
        }

        val graph = randomGraph(n, 2 * n, random)

        val scenario = scenarioGenerator.generate(graph, threads, actorsPerThread, 2, 1, false, 1)

        val initExecution = scenario.initialEdges.map { addEdgeActor(it.to(), it.from()) }
        val postExecution = graph.edges.map { connectedActor(it.to(), it.from()) }
        val parallelExecution = scenario.queries.map {
            it.map { op -> createActor(op) }
        }

        return ExecutionScenario(initExecution, parallelExecution, postExecution)
    }

    private fun addEdgeActor(u: Int, v: Int) = Actor(addEdgeMethod, listOf(u, v), emptyList())
    private fun removeEdgeActor(u: Int, v: Int) = Actor(removeEdgeMethod, listOf(u, v), emptyList())
    private fun connectedActor(u: Int, v: Int) = Actor(connectedMethod, listOf(u, v), emptyList())
}