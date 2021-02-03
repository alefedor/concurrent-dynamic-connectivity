package benchmarks

import benchmarks.util.*
import benchmarks.util.executors.ScenarioExecutor
import benchmarks.util.generators.RandomScenarioGenerator
import benchmarks.util.generators.TwoLevelScenarioGenerator
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

private const val components = 500
private const val nodesPerComponent = 70

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
open class CommonDynamicConnectivityTwoLevelBenchmark {
    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: DCPForModificationsConstructor = DCPForModificationsConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128")
    open var workers: Int = 0

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        scenario = TwoLevelScenarioGenerator()
            .generate(components, nodesPerComponent, workers, 2000000 / workers)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = ScenarioExecutor(
            scenario,
            { size -> dcpConstructor.constructor()(size, workers + 1) })
    }

    @Setup(Level.Iteration)
    fun flushOut() {
        println()
    }
}

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
open class LockElisionDynamicConnectivityTwoLevelBenchmark {
    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: LockElisionDCPForModificationsConstructor = LockElisionDCPForModificationsConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128")
    open var workers: Int = 0


    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        scenario = TwoLevelScenarioGenerator()
            .generate(components, nodesPerComponent, workers, 2000000 / workers)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = ScenarioExecutor(scenario, dcpConstructor.constructor())
    }

    @Setup(Level.Iteration)
    fun flushOut() {
        println()
    }
}