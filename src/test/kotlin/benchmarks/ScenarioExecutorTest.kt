package benchmarks

import benchmarks.util.Graph
import benchmarks.util.executors.ScenarioExecutor
import benchmarks.util.generators.RandomScenarioGenerator
import benchmarks.util.bidirectionalEdge
import connectivity.concurrent.general.NBReadsCoarseGrainedLockingDynamicConnectivity
import org.junit.Test

class ScenarioExecutorTest {
    @Test
    fun test() {
        val edges = longArrayOf(
            bidirectionalEdge(0, 1),
            bidirectionalEdge(0, 2),
            bidirectionalEdge(0, 4),
            bidirectionalEdge(1, 2),
            bidirectionalEdge(1, 4),
            bidirectionalEdge(1, 5),
            bidirectionalEdge(2, 3),
            bidirectionalEdge(2, 4),
            bidirectionalEdge(2, 7),
            bidirectionalEdge(3, 5),
            bidirectionalEdge(3, 7),
            bidirectionalEdge(3, 8),
            bidirectionalEdge(4, 5),
            bidirectionalEdge(5, 7),
            bidirectionalEdge(5, 8),
            bidirectionalEdge(6, 7),
            bidirectionalEdge(7, 8)
        )

        val graph = Graph(9, edges)
        val scenario = RandomScenarioGenerator()
            .generate(graph, 3, 6, 1, 1, true, 1)

        repeat(1000) {
            val executor = ScenarioExecutor(
                scenario,
                ::NBReadsCoarseGrainedLockingDynamicConnectivity
            )
            executor.run()
        }
    }
}