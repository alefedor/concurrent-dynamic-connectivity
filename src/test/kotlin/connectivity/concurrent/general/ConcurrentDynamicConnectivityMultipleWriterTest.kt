package connectivity.concurrent.general

import connectivity.concurrent.GeneralDynamicConnectivityMultipleWriterExecutionGenerator
import connectivity.concurrent.general.major.log
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
private const val n3 = 9

private const val actorsPerThread = 7
private const val iterations = 1000

@RunWith(Parameterized::class)
class ConcurrentDynamicConnectivityMultipleWriterTest(dcp: ConcurrentGeneralDynamicConnectivityConstructor) {

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
        requireStateEquivalenceImplCheck = false
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
        requireStateEquivalenceImplCheck = false
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n2 - 1}")
    class LinCheckDynamicConnectivityConcurrentStressTest2 {
        private val dc = globalDcpConstructor.construct(n2)

        init {
            log.clear()
        }

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

    @StressCTest(
        actorsAfter = 2 * n3,
        actorsBefore = n3,
        actorsPerThread = actorsPerThread,
        iterations = iterations,
        generator = GeneralDynamicConnectivityMultipleWriterExecutionGenerator::class,
        minimizeFailedScenario = false,
        requireStateEquivalenceImplCheck = false
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n3 - 1}")
    class LinCheckDynamicConnectivityConcurrentStressTest3 {
        private val dc = globalDcpConstructor.construct(n3)

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

    @Test
    fun test3() {
        LinChecker.check(LinCheckDynamicConnectivityConcurrentStressTest3::class.java)
    }
}