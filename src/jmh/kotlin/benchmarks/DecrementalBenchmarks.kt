package benchmarks

import benchmarks.util.*
import benchmarks.util.executors.SuccessiveScenarioExecutor
import benchmarks.util.generators.DecrementalScenarioGenerator
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = 1, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = 1, timeUnit = TimeUnit.SECONDS)
open class CommonDynamicConnectivityDecrementalBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: SuccessiveScenarioExecutor

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
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        scenario = DecrementalScenarioGenerator()
            .generate(graph, workers)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = SuccessiveScenarioExecutor(
            scenario,
            { size -> dcpConstructor.construct(size, workers + 1) })
    }

    @Setup(Level.Invocation)
    fun flushOut() {
        println()
    }
}

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = 1, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = 1, timeUnit = TimeUnit.SECONDS)
open class LockElisionDynamicConnectivityDecrementalBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: SuccessiveScenarioExecutor

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
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        scenario = DecrementalScenarioGenerator()
            .generate(graph, workers)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = SuccessiveScenarioExecutor(scenario, dcpConstructor.construct)
    }

    @Setup(Level.Invocation)
    fun flushOut() {
        println()
    }
}