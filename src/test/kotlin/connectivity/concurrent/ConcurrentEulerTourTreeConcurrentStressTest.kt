package connectivity.concurrent

import connectivity.concurrent.tree.ConcurrentEulerTourTree
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState
import org.junit.Test

@StressCTest(actorsAfter = 3, actorsBefore = 3, iterations = 1000, generator = EulerTourTreeExecutionGenerator::class)
@Param(name = "a", gen = IntGen::class, conf = "0:4")
@OpGroupConfig(name = "writer", nonParallel = true)
class ConcurrentEulerTourTreeConcurrentStressTest : VerifierState() {
    private val n = 5
    private val dc = ConcurrentEulerTourTree(n)

    @Operation(group = "writer")
    fun addEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
        dc.addTreeEdge(a, b)
    }

    @Operation(group = "writer")
    fun removeEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
        dc.removeTreeEdge(a, b)
    }

    @Operation
    fun connected(@Param(name = "a") a: Int, @Param(name = "a") b: Int) = dc.connected(a, b)

    @Test
    fun test() {
        LinChecker.check(this::class.java)
    }

    override fun extractState() = dc.getEdges()
}