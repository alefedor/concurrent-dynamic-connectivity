package connectivity.concurrent.tree

import connectivity.concurrent.TreeDynamicConnectivityExecutionGenerator
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState
import org.junit.Test

private const val n = 6

@StressCTest(actorsAfter = 5, actorsBefore = 5, iterations = 1000, generator = TreeDynamicConnectivityExecutionGenerator::class)
@Param(name = "a", gen = IntGen::class, conf = "0:${connectivity.concurrent.general.n1 - 1}")
@OpGroupConfig(name = "writer", nonParallel = true)
class ConcurrentEulerTourTreeConcurrentStressTest : VerifierState() {
    private val dc = ConcurrentEulerTourTree(n)

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

    override fun extractState() = dc.state()
}