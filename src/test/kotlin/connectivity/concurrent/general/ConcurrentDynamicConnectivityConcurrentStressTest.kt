package connectivity.concurrent.general

import connectivity.concurrent.GeneralDynamicConnectivityExecutionGenerator
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState
import org.junit.Test

const val n = 7

@StressCTest(actorsAfter = 10, actorsBefore = 10, actorsPerThread = 8, iterations = 10000, generator = GeneralDynamicConnectivityExecutionGenerator::class, requireStateEquivalenceImplCheck = false)
@Param(name = "a", gen = IntGen::class, conf = "0:${n - 1}")
@OpGroupConfig(name = "writer", nonParallel = true)
class ConcurrentDynamicConnectivityConcurrentStressTest {
    private val dc = ImprovedCoarseGrainedLockingDynamicConnectivity(n)

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

    @Test
    fun test() {
        LinChecker.check(this::class.java)
    }
}