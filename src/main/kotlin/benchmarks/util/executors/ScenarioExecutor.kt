package benchmarks.util.executors

import benchmarks.util.*
import benchmarks.util.generators.OVERHEAD_RATIO
import connectivity.sequential.general.DynamicConnectivity
import kotlinx.atomicfu.atomic
import thirdparty.Aksenov239.fc.FCDynamicGraph
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

const val workAmount = 60 // simulate some other work
private const val BATCH_SIZE = 15 // increase counter in batches to reduce contention
private const val DELETE_PERCENTAGE = 0.01

class ScenarioExecutor(val scenario: Scenario, dcpConstructor: (Int) -> DynamicConnectivity) {
    private val dcp = dcpConstructor(scenario.nodes)

    private val threads: Array<Thread>

    private val operationsExecuted = atomic(0)

    @Volatile
    private var start = false

    init {
        if (dcp is FCDynamicGraph) {
            val request = FCDynamicGraph.Request()
            for (edge in scenario.initialEdges) {
                request.set(0, edge.from(), edge.to())
                dcp.addEdge(request)
            }
        } else {
            for (edge in scenario.initialEdges) {
                dcp.addEdge(edge.from(), edge.to())
            }
        }
        val rnd = Random(547567)
        // delete some edges to promote some edges from level 0
        repeat((scenario.initialEdges.size * DELETE_PERCENTAGE).toInt()) {
            val edge = scenario.initialEdges.random(rnd)
            if (dcp is FCDynamicGraph) {
                val request = FCDynamicGraph.Request()
                request.set(0, edge.from(), edge.to())
                dcp.removeEdge(request)

            } else {
                dcp.removeEdge(edge.from(), edge.to())
            }
        }

        val expectedOverhead = scenario.threads * BATCH_SIZE / 2
        val operationsNeeded = scenario.threads * scenario.queries[0].size / OVERHEAD_RATIO - expectedOverhead
        val threadsInitialized = AtomicInteger(0)

        threads = Array(scenario.threads) { threadId ->
            BenchmarkThread(threadId) {
                val queries = scenario.queries[threadId]

                threadsInitialized.incrementAndGet()

                while (!start); // wait until start

                var idRemainder = 0

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
                    work(workAmount)

                    idRemainder++
                    if (idRemainder == BATCH_SIZE) {
                        idRemainder = 0
                        val ops = operationsExecuted.addAndGet(BATCH_SIZE)
                        if (ops >= operationsNeeded) break
                    }
                }
            }
        }
        threads.forEach { it.start() }
        while (threadsInitialized.get() != scenario.threads); // wait until all threads are initialized
    }

    fun run() {
        start = true
        threads.forEach { it.join() }
    }

    private inline fun work(amount: Int) {
        val p = 1.0 / amount
        val r = ThreadLocalRandom.current()
        while (true) {
            if (r.nextDouble() < p) break
        }
    }
}