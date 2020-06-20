package connectivity.concurrent.general

import connectivity.concurrent.GeneralDynamicConnectivitySingleWriterExecutionGenerator
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val n1 = 7
private const val n2 = 9
private const val n3 = 11

private const val iterations = 1000
private const val invocations = 4000

@RunWith(Parameterized::class)
class ConcurrentDynamicConnectivitySingleWriterTest(constructorId: Int) {

    init {
        globalDcpConstructor = ConcurrentGeneralDynamicConnectivityConstructor.values()[constructorId]
    }

    @StressCTest(
        actorsAfter = 10,
        actorsBefore = 10,
        actorsPerThread = 10,
        iterations = iterations,
        invocationsPerIteration = invocations,
        generator = GeneralDynamicConnectivitySingleWriterExecutionGenerator::class,
        requireStateEquivalenceImplCheck = false,
        minimizeFailedScenario = false
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n1 - 1}")
    @OpGroupConfig(name = "writer", nonParallel = true)
    class LinCheckDynamicConnectivityConcurrentStressTest1 {
        private val dc = globalDcpConstructor.construct(n1)

        @Operation(group = "writer")
        fun addEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.addEdge(a, b)
        }

        @Operation(group = "writer")
        fun removeEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.removeEdge(a, b)
        }

        @Operation
        fun connected(@Param(name = "a") a: Int, @Param(name = "a") b: Int) = dc.connected(a, b)
    }

    // copy-paste because of compile-time constants
    @StressCTest(
        actorsAfter = 10,
        actorsBefore = 10,
        actorsPerThread = 10,
        iterations = iterations,
        invocationsPerIteration = invocations,
        generator = GeneralDynamicConnectivitySingleWriterExecutionGenerator::class,
        requireStateEquivalenceImplCheck = false,
        minimizeFailedScenario = false
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n2 - 1}")
    @OpGroupConfig(name = "writer", nonParallel = true)
    class LinCheckDynamicConnectivityConcurrentStressTest2 {
        private val dc = globalDcpConstructor.construct(n2)

        @Operation(group = "writer")
        fun addEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.addEdge(a, b)
        }

        @Operation(group = "writer")
        fun removeEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.removeEdge(a, b)
        }

        @Operation
        fun connected(@Param(name = "a") a: Int, @Param(name = "a") b: Int) = dc.connected(a, b)
    }

    @StressCTest(
        actorsAfter = 10,
        actorsBefore = 10,
        actorsPerThread = 10,
        iterations = iterations,
        invocationsPerIteration = invocations,
        generator = GeneralDynamicConnectivitySingleWriterExecutionGenerator::class,
        requireStateEquivalenceImplCheck = false,
        minimizeFailedScenario = false
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n3 - 1}")
    @OpGroupConfig(name = "writer", nonParallel = true)
    class LinCheckDynamicConnectivityConcurrentStressTest3 {
        private val dc = globalDcpConstructor.construct(n3)

        @Operation(group = "writer")
        fun addEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.addEdge(a, b)
        }

        @Operation(group = "writer")
        fun removeEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
            dc.removeEdge(a, b)
        }

        @Operation
        fun connected(@Param(name = "a") a: Int, @Param(name = "a") b: Int) = dc.connected(a, b)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun dcpConstructorIds() = Array(ConcurrentGeneralDynamicConnectivityConstructor.values().size) { it }
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