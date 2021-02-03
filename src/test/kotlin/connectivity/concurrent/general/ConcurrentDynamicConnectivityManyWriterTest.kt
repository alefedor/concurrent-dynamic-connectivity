package connectivity.concurrent.general

import connectivity.concurrent.GeneralDynamicConnectivityMultipleWriterExecutionGenerator
import connectivity.concurrent.general.major.*
import connectivity.concurrent.general.major_coarse_grained.*
import connectivity.sequential.SlowConnectivity
import connectivity.sequential.general.*
import org.jetbrains.kotlinx.lincheck.LinChecker
import org.jetbrains.kotlinx.lincheck.annotations.OpGroupConfig
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.execution.*
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val n1 = 5
private const val n2 = 7

private const val actorsPerThread = 3
private const val stressIterations = 50
private const val modelCheckingIterations = 3
private const val invocations = 4000
private const val threads = 3

abstract class LincheckManyThreadsTest(val minimizeScenario: Boolean, val executionGenerator: Class<out ExecutionGenerator>?) {
    protected abstract val dc: DynamicConnectivity

    @Operation
    fun addEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
        if (a != b)
            dc.addEdge(a, b)
    }

    @Operation
    fun removeEdge(@Param(name = "a") a: Int, @Param(name = "a") b: Int) {
        if (a != b)
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
            if (executionGenerator != null)
                executionGenerator(executionGenerator)
            minimizeFailedScenario(minimizeScenario)
            requireStateEquivalenceImplCheck(false)
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
            if (executionGenerator != null)
                executionGenerator(executionGenerator)
            minimizeFailedScenario(minimizeScenario)
            requireStateEquivalenceImplCheck(false)
            sequentialSpecification(DynamicConnectivitySequentialSpecification::class.java)
            threads(threads)
        }
        LinChecker.check(this::class.java, options)
    }
}

@Param(name = "a", gen = IntGen::class, conf = "0:${n1 - 1}")
abstract class LinCheckDynamicConnectivityManyThreadsTest1(
    dynamicConnectivityConstructor: (Int) -> DynamicConnectivity,
    minimizeScenario: Boolean,
    executionGenerator: Class<out ExecutionGenerator>?
) : LincheckManyThreadsTest(minimizeScenario, executionGenerator) {
    override val dc = dynamicConnectivityConstructor.invoke(n1)
}

@Param(name = "a", gen = IntGen::class, conf = "0:${n2 - 1}")
abstract class LinCheckDynamicConnectivityManyThreadsTest2(
    dynamicConnectivityConstructor: (Int) -> DynamicConnectivity,
    minimizeScenario: Boolean,
    executionGenerator: Class<out ExecutionGenerator>?
) : LincheckManyThreadsTest(minimizeScenario, executionGenerator) {
    override val dc = dynamicConnectivityConstructor(n2)
}

class MajorDCManyThreadsTest1 : LinCheckDynamicConnectivityManyThreadsTest1(::MajorDynamicConnectivity, true, GeneralDynamicConnectivityMultipleWriterExecutionGenerator::class.java)
class MajorDCManyThreadsTest2 : LinCheckDynamicConnectivityManyThreadsTest2(::MajorDynamicConnectivity, true, GeneralDynamicConnectivityMultipleWriterExecutionGenerator::class.java)
class MajorDCManyThreadsTest3 : LinCheckDynamicConnectivityManyThreadsTest1(::MajorDynamicConnectivity, true, null)
class MajorDCManyThreadsTest4 : LinCheckDynamicConnectivityManyThreadsTest2(::MajorDynamicConnectivity, true, null)

