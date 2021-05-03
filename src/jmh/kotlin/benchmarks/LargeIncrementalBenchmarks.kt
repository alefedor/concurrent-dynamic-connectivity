package benchmarks

import benchmarks.util.DCPForModificationsConstructor
import benchmarks.util.LockElisionDCPForModificationsConstructor
import benchmarks.util.Scenario
import benchmarks.util.constructor
import benchmarks.util.executors.SuccessiveScenarioExecutor
import benchmarks.util.generators.IncrementalScenarioGenerator
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.DAYS)
@Timeout(time = 1, timeUnit = TimeUnit.DAYS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.DAYS)
open class LargeCommonDynamicConnectivityIncrementalBenchmark {
    @Param
    open var graph: LargeGraph = LargeGraph.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: SuccessiveScenarioExecutor

    @Param
    open var dcpConstructor: DCPForModificationsConstructor = DCPForModificationsConstructor.values()[0]

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = LargeGraphServer.getLookup().graphByParams(graph)
        scenario = IncrementalScenarioGenerator()
            .generate(graph, MAX_WORKERS)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = SuccessiveScenarioExecutor(
            scenario,
            { size -> dcpConstructor.constructor()(size, MAX_WORKERS + 1) })
        System.gc()

    }

    @Setup(Level.Iteration)
    fun flushOut() {
        println()
    }
}

@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.DAYS)
@Timeout(time = 1, timeUnit = TimeUnit.DAYS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.DAYS)
open class LargeLockElisionDynamicConnectivityIncrementalBenchmark {
    @Param
    open var graph: LargeGraph = LargeGraph.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: SuccessiveScenarioExecutor

    @Param
    open var dcpConstructor: LockElisionDCPForModificationsConstructor = LockElisionDCPForModificationsConstructor.values()[0]

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = LargeGraphServer.getLookup().graphByParams(graph)
        scenario = IncrementalScenarioGenerator()
            .generate(graph, MAX_WORKERS)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = SuccessiveScenarioExecutor(scenario, dcpConstructor.constructor())
        System.gc()
    }

    @Setup(Level.Iteration)
    fun flushOut() {
        println()
    }
}