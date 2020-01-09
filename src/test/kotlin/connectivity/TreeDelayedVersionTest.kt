package connectivity

import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressCTest
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState
import org.junit.Test
import java.util.concurrent.atomic.AtomicIntegerArray

@StressCTest(iterations = 10000, invocationsPerIteration = 1000, actorsBefore = 2, actorsAfter = 2)
@Param(name = "a", gen = IntGen::class, conf = "0:3")
@OpGroupConfig(name = "writer", nonParallel = true)
class TreeDelayedVersionTest : VerifierState() {
    override fun extractState(): Any {
        val result = mutableListOf<Int>()
        for (i in 0 until 5) {
            result.add(parent[i])
            result.add(version[i])
        }
        return result
    }

    private val parent: AtomicIntegerArray
    private val version: AtomicIntegerArray

    init {
        parent = AtomicIntegerArray(5)
        for (i in 0 until 5) parent[i] = -1
        version = AtomicIntegerArray(5)
        for (i in 0 until 5) version[i] = 0
    }

    @Operation(group = "writer")
    fun add(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
        if (a != b) {
            var v = b
            while (v != -1) {
                if (v == a) return
                v = parent[v]
            }

            if (parent.compareAndSet(a, -1, b))
                version.incrementAndGet(root(a))
        }

    }

    @Operation(group = "writer")
    fun remove(@Param(name = "a") a: Int) {
        val was = parent[a]
        if (was != -1) {
            version.incrementAndGet(a)
            parent.set(a, -1)
            version.incrementAndGet(root(was))
        }
    }

    @Operation
    fun inSame(@Param(name = "a") a: Int, @Param(name = "a") b: Int): Boolean {
        if (a == b) return true

        while (true) {
            val ra1 = get(root(a))
            val rb1 = get(root(b))
            val ra2 = get(root(a))
            val rb2 = get(root(b))
            if (ra1 == ra2 && rb1 == rb2)
                return ra1 == rb1
        }
    }

    private fun get(root: Int) = Pair(root, version[root])

    private fun root(w: Int): Int {
        var v = w
        var u = parent.get(v)
        while (u != -1) {
            v = u
            u = parent.get(v)
        }
        return v
    }

    @Test
    fun test() {
        LinChecker.check(this::class.java)
    }
}