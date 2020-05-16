package benchmarks.util.executors

import benchmarks.util.*
import benchmarks.util.generators.OVERHEAD_RATIO
import connectivity.sequential.general.DynamicConnectivity
import kotlinx.atomicfu.atomic
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

const val workAmount = 40
private const val BATCH_SIZE = 10 // increase counter in batches to reduce contention

class ScenarioExecutor(val scenario: Scenario, dcpConstructor: (Int) -> DynamicConnectivity) {
    private val dcp = dcpConstructor(scenario.nodes)

    private val threads: Array<Thread>

    private val operationsExecuted = atomic(0)

    @Volatile
    private var start = false

    init {
        for (edge in scenario.initialEdges)
            dcp.addEdge(edge.from(), edge.to())

        val expectedOverhead = scenario.threads * BATCH_SIZE / 2
        val operationsNeeded = scenario.threads * scenario.queries[0].size / OVERHEAD_RATIO - expectedOverhead
        val threadsInitialized = AtomicInteger(0)

        threads = Array(scenario.threads) { threadId ->
            Thread {
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