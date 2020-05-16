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
        val updateWeight = if (readWeight != 9999) 1 else 0
        val readWeight = if (readWeight != 9999) readWeight else 1
        scenario = RandomScenarioGenerator()
            .generate(graph, workers, totalSize / workers, updateWeight, readWeight, true, 3)
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
        val updateWeight = if (readWeight != 9999) 1 else 0
        val readWeight = if (readWeight != 9999) readWeight else 1
        scenario = RandomScenarioGenerator()
            .generate(graph, workers, totalSize / workers, updateWeight, readWeight, true, 3)
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