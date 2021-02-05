package connectivity.concurrent

import com.google.common.collect.*
import connectivity.*
import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.paramgen.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.junit.*

@Param(name = "key", gen = LongGen::class, conf = "0:4")
class ConcurrentMultisetTest {
    private val ms = ConcurrentHashMultiset.create<Long>()

    @Operation
    fun add(@Param(name = "key") key: Long) = ms.add(key)

    @Operation
    fun remove(@Param(name = "key") key: Long) = ms.remove(key)

    @Operation
    fun contains(@Param(name = "key") key: Long): Boolean {
        val it = ms.iterator()
        while (it.hasNext()) {
            if (key == it.next())
                return true
        }
        return false
    }
    @Test
    fun modelCheckingTest() {
        val options = ModelCheckingOptions().apply {
            threads(3)
            actorsPerThread(3)
            iterations(300)
            minimizeFailedScenario(false)
            requireStateEquivalenceImplCheck(false)
        }
        LinChecker.check(this::class.java, options)
    }
}