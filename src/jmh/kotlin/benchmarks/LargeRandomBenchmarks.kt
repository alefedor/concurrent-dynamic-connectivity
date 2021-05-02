package benchmarks

import benchmarks.util.DCPConstructor
import benchmarks.util.LockElisionDCPConstructor
import benchmarks.util.Scenario
import benchmarks.util.constructor
import benchmarks.util.executors.ScenarioExecutor
import benchmarks.util.generators.FullyRandomScenarioGenerator
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

const val MAX_WORKERS = 144
const val LARGE_SCENARIO_SIZE = 400_000_000

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 1, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 0, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
open class LargeCommonDynamicConnectivityRandomBenchmark {
    @Param
    open var graph: LargeGraph = LargeGraph.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param("CoarseGrainedLockingDCP", "FineGrainedLockingDCP", "NBReadsCoarseGrainedLockingDCP", "NBReadsFineGrainedLockingDynamicConnectivity", "NBFCDynamicConnectivity", "MajorDynamicConnectivity", "MajorCoarseGrainedDynamicConnectivity", "FCReadOptimizedDynamicConnectivity")
    open var dcpConstructor: DCPConstructor = DCPConstructor.values()[0]

    @Param("4", "99")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = LargeGraphServer.getLookup().graphByParams(graph)
        println("Graph: ${this.graph}, |V| = ${graph.nodes}, |E| = ${graph.edges.size}")
        val updateWeight = 1
        val readWeight = readWeight
        scenario = FullyRandomScenarioGenerator()
            .generate(graph, MAX_WORKERS, LARGE_SCENARIO_SIZE / MAX_WORKERS, updateWeight, readWeight, true, 1)
    }

    @Setup(Level.Invocation)
    fun initializeInvocation() {
        scenarioExecutor = ScenarioExecutor(
            scenario,
            { size -> dcpConstructor.constructor()(size, MAX_WORKERS + 1) })
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
@Warmup(iterations = 0, time = TIME_IN_SECONDS, timeUnit = TimeUnit.SECONDS)
open class LargeLockElisionDynamicConnectivityRandomBenchmark {
    @Param
    open var graph: LargeGraph = LargeGraph.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: LockElisionDCPConstructor = LockElisionDCPConstructor.values()[0]

    @Param("4", "99")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = LargeGraphServer.getLookup().graphByParams(graph)
        val updateWeight = 1
        val readWeight = readWeight
        scenario = FullyRandomScenarioGenerator()
            .generate(graph, MAX_WORKERS, LARGE_SCENARIO_SIZE / MAX_WORKERS, updateWeight, readWeight, true, 1)
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