package benchmarks.util.executors

import benchmarks.util.*
import connectivity.sequential.general.DynamicConnectivity
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import thirdparty.Aksenov239.fc.FCDynamicGraph
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

private const val BATCH_SIZE = 15 // increase counter in batches to reduce contention

class SuccessiveScenarioExecutor(val scenario: Scenario, dcpConstructor: (Int) -> DynamicConnectivity) {
    private val dcp = dcpConstructor(scenario.nodes)

    private val pos: AtomicInt = atomic(0)
    private val threads: Array<Thread>

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

        val threadsInitialized = AtomicInteger(0)

        threads = Array(scenario.threads) { threadId ->
            BenchmarkThread(threadId) {
                val queries = scenario.queries[0]

                threadsInitialized.incrementAndGet()

                while (!start); // wait until start

                val queriesSize = queries.size

                while (true) {
                    val idStart = pos.getAndAdd(BATCH_SIZE)
                    if (idStart >= queriesSize) break

                    for (id in idStart until (idStart + BATCH_SIZE)) {
                        if (id >= queriesSize) break
                        val query = queries[id]
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