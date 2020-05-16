package connectivity.concurrent.general

import benchmarks.util.generators.RandomScenarioGenerator
import benchmarks.util.executors.ScenarioExecutor
import benchmarks.util.randomDividedGraph
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.system.measureTimeMillis

@RunWith(Parameterized::class)
class PerformanceTest(dcp: ConcurrentGeneralDynamicConnectivityConstructor) {
    init {
        globalDcpConstructor = dcp
    }

    @Test
    fun testOneThread() {
        val iterations = 40
        val warmupIterations = 10
        val time = (0 until (iterations + warmupIterations)).map {
            measureTimeMillis {
                ScenarioExecutor(
                    scenarioOneThread,
                    globalDcpConstructor.construct
                ).run()
            }
        }.drop(warmupIterations).average()
        println("$globalDcpConstructor: $time ms")
    }

    @Test
    fun testTwoThreads() {
        val iterations = 40
        val warmupIterations = 10
        val time = (0 until (iterations + warmupIterations)).map {
            measureTimeMillis {
                ScenarioExecutor(
                    scenarioTwoThreads,
                    globalDcpConstructor.construct
                ).run()
            }
        }.drop(warmupIterations).average()
        println("$globalDcpConstructor: $time ms")
    }

    companion object {
        private val graph = randomDividedGraph(3, 200, 3000)

        private val scenarioOneThread = RandomScenarioGenerator()
            .generate(graph, 1, 100000, 1, 1, true)
        private val scenarioTwoThreads = RandomScenarioGenerator()
            .generate(graph, 2, 100000, 1, 1, true)

        @JvmStatic
        @Parameterized.Parameters
        fun dcpConstructors() = ConcurrentGeneralDynamicConnectivityConstructor.values()
    }
}