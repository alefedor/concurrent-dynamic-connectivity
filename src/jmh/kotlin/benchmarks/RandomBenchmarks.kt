package benchmarks

import benchmarks.util.*
import benchmarks.util.executors.ScenarioExecutor
import benchmarks.util.generators.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

private const val TOTAL_SCENARIO_SIZE = 15_000_000

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
open class SmallCommonDynamicConnectivityRandomBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: DCPConstructor = DCPConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128", "144")
    open var workers: Int = 0

    @Param("4", "99")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        val updateWeight = if (readWeight != 9999) 1 else 0
        val readWeight = if (readWeight != 9999) readWeight else 1
        scenario = FullyRandomScenarioGenerator()
            .generate(graph, workers, TOTAL_SCENARIO_SIZE / workers, updateWeight, readWeight, true, 1)
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
open class SmallLockElisionDynamicConnectivityRandomBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: LockElisionDCPConstructor = LockElisionDCPConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128", "144")
    open var workers: Int = 0

    @Param("4", "99")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        val updateWeight = if (readWeight != 9999) 1 else 0
        val readWeight = if (readWeight != 9999) readWeight else 1
        scenario = FullyRandomScenarioGenerator()
            .generate(graph, workers, TOTAL_SCENARIO_SIZE / workers, updateWeight, readWeight, true, 1)
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