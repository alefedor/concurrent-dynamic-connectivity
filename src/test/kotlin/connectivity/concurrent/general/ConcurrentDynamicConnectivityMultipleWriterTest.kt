package connectivity.concurrent.general

import connectivity.concurrent.GeneralDynamicConnectivityMultipleWriterExecutionGenerator
import connectivity.concurrent.general.major.*
import connectivity.sequential.SlowConnectivity
import connectivity.sequential.general.*
import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*

private const val n1 = 5
private const val n2 = 7
private const val n3 = 9

private const val actorsPerThread = 5
private const val stressIterations = 5000
private const val modelCheckingIterations = 100
private const val invocations = 6000
private const val threads = 2

class DynamicConnectivitySequentialSpecification() {
    val slowConnectivity = SlowConnectivity(9)
    fun addEdge(u: Int, v: Int) = slowConnectivity.addEdge(u, v)
    fun removeEdge(u: Int, v: Int) = slowConnectivity.removeEdge(u, v)
    fun connected(u: Int, v: Int) = slowConnectivity.sameComponent(u, v)
}

abstract class LincheckTest {
    protected abstract val dc: DynamicConnectivity

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


    @Test
    fun stress() {
        val options = StressOptions().apply {
            actorsPerThread(actorsPerThread)
            iterations(stressIterations)
            invocationsPerIteration(invocations)
            executionGenerator(GeneralDynamicConnectivityMultipleWriterExecutionGenerator::class.java)
            requireStateEquivalenceImplCheck(false)
            minimizeFailedScenario(false)
            sequentialSpecification(DynamicConnectivitySequentialSpecification::class.java)
            threads(threads)
        }
        LinChecker.check(this::class.java, options)
    }

    @Test
    fun modelChecking() {
        val options = ModelCheckingOptions().apply {
            actorsPerThread(actorsPerThread)
            iterations(modelCheckingIterations)
            invocationsPerIteration(invocations)
            executionGenerator(GeneralDynamicConnectivityMultipleWriterExecutionGenerator::class.java)
            requireStateEquivalenceImplCheck(false)
            minimizeFailedScenario(false)
            sequentialSpecification(DynamicConnectivitySequentialSpecification::class.java)
            threads(threads)
        }
        LinChecker.check(this::class.java, options)
    }
}

@Param(name = "a", gen = IntGen::class, conf = "0:${n1 - 1}")
abstract class LinCheckDynamicConnectivityTest1(dynamicConnectivityConstructor: (Int) -> DynamicConnectivity) : LincheckTest() {
    override val dc = dynamicConnectivityConstructor.invoke(n1)
}

@Param(name = "a", gen = IntGen::class, conf = "0:${n2 - 1}")
abstract class LinCheckDynamicConnectivityTest2(dynamicConnectivityConstructor: (Int) -> DynamicConnectivity) : LincheckTest() {
    override val dc = dynamicConnectivityConstructor(n2)
}

@Param(name = "a", gen = IntGen::class, conf = "0:${n3 - 1}")
abstract class LinCheckDynamicConnectivityTest3(dynamicConnectivityConstructor: (Int) -> DynamicConnectivity) : LincheckTest() {
    override val dc = dynamicConnectivityConstructor(n3)
}

class MajorDCTest1 : LinCheckDynamicConnectivityTest1(::MajorDynamicConnectivity)
class MajorDCTest2 : LinCheckDynamicConnectivityTest2(::MajorDynamicConnectivity)
class MajorDCTest3 : LinCheckDynamicConnectivityTest3(::MajorDynamicConnectivity)

