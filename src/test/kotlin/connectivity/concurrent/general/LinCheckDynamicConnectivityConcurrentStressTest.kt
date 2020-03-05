package connectivity.concurrent.general

import connectivity.concurrent.GeneralDynamicConnectivityExecutionGenerator
import connectivity.sequential.general.DynamicConnectivity
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

const val n = 7

enum class ConcurrentGeneralDynamicConnectivityConstructor(val construct: (size: Int) -> DynamicConnectivity) {
    CoarseGrainedLockingDynamicConnectivity(::CoarseGrainedLockingDynamicConnectivity),
    ImprovedCoarseGrainedLockingDynamicConnectivity(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDynamicConnectivity(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    FineGrainedLockingDynamicConnectivity(::FineGrainedLockingDynamicConnectivity),
    SFineGrainedLockingDynamicConnectivity(::SFineGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteFairLockingDynamicConnectivity(::CoarseGrainedReadWriteFairLockingDynamicConnectivity),
    FineGrainedFairLockingDynamicConnectivity(::FineGrainedFairLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    ImprovedFineGrainedLockingDynamicConnectivity(::ImprovedFineGrainedLockingDynamicConnectivity)
}

var globalDcpConstructor: ConcurrentGeneralDynamicConnectivityConstructor = ConcurrentGeneralDynamicConnectivityConstructor.CoarseGrainedLockingDynamicConnectivity


@RunWith(Parameterized::class)
class ConcurrentDynamicConnectivityTest(private val dcp: ConcurrentGeneralDynamicConnectivityConstructor) {

    init {
        globalDcpConstructor = dcp
    }

    @StressCTest(
        actorsAfter = 10,
        actorsBefore = 10,
        actorsPerThread = 8,
        iterations = 5000,
        generator = GeneralDynamicConnectivityExecutionGenerator::class,
        requireStateEquivalenceImplCheck = false
    )
    @Param(name = "a", gen = IntGen::class, conf = "0:${n - 1}")
    @OpGroupConfig(name = "writer", nonParallel = true)
    class LinCheckDynamicConnectivityConcurrentStressTest {
        private val dc = globalDcpConstructor.construct(n)

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
        fun dcpConstructors() = ConcurrentGeneralDynamicConnectivityConstructor.values()
    }

    @Test
    fun test() {
        LinChecker.check(LinCheckDynamicConnectivityConcurrentStressTest::class.java)
    }
}