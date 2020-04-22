package connectivity.concurrent.general

import connectivity.concurrent.GeneralDynamicConnectivityMultipleWriterExecutionGenerator
import connectivity.sequential.SlowConnectivity
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val n1 = 5
private const val n2 = 7

private const val actorsPerThread = 4
private const val iterations = 200
private const val threads = 3

@RunWith(Parameterized::class)
class ConcurrentDynamicConnectivityManyWriterTest(dcp: ConcurrentGeneralDynamicConnectivityConstructor) {

    init {
        globalDcpConstructor = dcp
    }

    class DynamicConnectivitySequentialSpecification() {
        val slowConnectivity = SlowConnectivity(9)
        fun addEdge(u: Int, v: Int) = slowConnectivity.addEdge(u, v)
        fun removeEdge(u: Int, v: Int) = slowConnectivity.removeEdge(u, v)
        fun connected(u: Int, v: Int) = slowConnectivity.sameComponent(u, v)
    }

    @StressCTest(
        actorsAfter = 2 * n1,
        actorsBefore = n1,
        actorsPerThread = actorsPerThread,
        iterations = iterations,
        generator = GeneralDynamicConnectivityMultipleWriterExecutionGenerator::class,
        minimizeFailedScenario = false,
        requireStateEquivalenceImplCheck = false,
        threads = threads
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n1 - 1}")
    @OpGroupConfig(name = "writer", nonParallel = true)
    class LinCheckDynamicConnectivityConcurrentStressTest1 {
        private val dc = globalDcpConstructor.construct(n1)

        @Operation
        fun addEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.addEdge(a, b)
        }

        @Operation
        fun removeEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.removeEdge(a, b)
        }

        @Operation
        fun connected(@Param(name = "a") a: Int, @Param(name = "a") b: Int) = dc.connected(a, b)
    }

    // copy-paste because of compile-time constants
    @StressCTest(
        actorsAfter = 2 * n2,
        actorsBefore = n2,
        actorsPerThread = actorsPerThread,
        iterations = iterations,
        generator = GeneralDynamicConnectivityMultipleWriterExecutionGenerator::class,
        minimizeFailedScenario = false,
        requireStateEquivalenceImplCheck = false,
        threads = threads
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n2 - 1}")
    class LinCheckDynamicConnectivityConcurrentStressTest2 {
        private val dc = globalDcpConstructor.construct(n2)

        @Operation
        fun addEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.addEdge(a, b)
        }

        @Operation
        fun removeEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.removeEdge(a, b)
        }

        @Operation
        fun connected(@Param(name = "a") a: Int, @Param(name = "a") b: Int) = dc.connected(a, b)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun dcpConstructors() = ConcurrentGeneralDynamicConnectivityConstructor.values()
    }

    @Test
    fun test1() {
        LinChecker.check(LinCheckDynamicConnectivityConcurrentStressTest1::class.java)
    }

    @Test
    fun test2() {
        LinChecker.check(LinCheckDynamicConnectivityConcurrentStressTest2::class.java)
    }
}