package connectivity.concurrent.general

import benchmarks.util.RandomScenarioGenerator
import benchmarks.util.ScenarioExecutor
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
    fun test() {
        val iterations = 40
        val warmupIterations = 10
        val time = (0 until (iterations + warmupIterations)).map {
            measureTimeMillis {
                ScenarioExecutor(scenario, globalDcpConstructor.construct).run()
            }
        }.drop(warmupIterations).average()
        println("$globalDcpConstructor: $time ms")
    }

    companion object {
        private val graph = randomDividedGraph(10, 500, 8000)

        private val scenario = RandomScenarioGenerator().generate(graph, 1, 100000, 1, 1, true)

        @JvmStatic
        @Parameterized.Parameters
        fun dcpConstructors() = ConcurrentGeneralDynamicConnectivityConstructor.values()
    }
}