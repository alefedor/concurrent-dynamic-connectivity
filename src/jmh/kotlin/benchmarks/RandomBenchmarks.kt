package benchmarks

import benchmarks.util.DCPConstructor
import benchmarks.util.LockElisionDCPConstructor
import benchmarks.util.Scenario
import benchmarks.util.executors.ScenarioExecutor
import benchmarks.util.generators.RandomScenarioGenerator
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = 1, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = 1, timeUnit = TimeUnit.SECONDS)
open class CommonDynamicConnectivityRandomBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: DCPConstructor = DCPConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128")
    open var workers: Int = 0

    @Param("1", "4", "19", "9999")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        val totalScenarioSize = getTotalScenarioSize(graphParams, readWeight)
        val updateWeight = if (readWeight != 9999) 1 else 0
        val readWeight = if (readWeight != 9999) readWeight else 1
        scenario = RandomScenarioGenerator()
            .generate(graph, workers, totalScenarioSize / workers, updateWeight, readWeight, true, 3)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = ScenarioExecutor(
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
open class LockElisionDynamicConnectivityRandomBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: LockElisionDCPConstructor = LockElisionDCPConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128")
    open var workers: Int = 0

    @Param("1", "4", "19", "9999")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        val totalScenarioSize = getTotalScenarioSize(graphParams, readWeight)
        val updateWeight = if (readWeight != 9999) 1 else 0
        val readWeight = if (readWeight != 9999) readWeight else 1
        scenario = RandomScenarioGenerator()
            .generate(graph, workers, totalScenarioSize / workers, updateWeight, readWeight, true, 3)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = ScenarioExecutor(scenario, dcpConstructor.construct)
    }

    @Setup(Level.Invocation)
    fun flushOut() {
        println()
    }
}

fun getTotalScenarioSize(graphParams: GraphParams, readWeight: Int): Int {
    var result = when(graphParams) {
        GraphParams.USA_ROADS -> 1000000
        GraphParams.RANDOM_N -> 1000000
        GraphParams.RANDOM_2N -> 1000000
        GraphParams.RANDOM_NLOG -> 4000000
        GraphParams.RANDOM_NSQRT -> 4000000
        GraphParams.TWITTER -> 4000000
        GraphParams.STANFORD_WEB -> 1000000
        GraphParams.RANDOM_DIVIDED -> 4000000
    }

    if (readWeight == 4)
        result *= 2

    if (readWeight == 19)
        result *= 4

    if (readWeight == 9999)
        result *= 10

    return result
}