package benchmarks

import benchmarks.util.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.results.format.ResultFormatType
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.RunnerException
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.TimeUnit

const val iterations = 8
const val warmupIterations = 2
const val totalSize = 5000000

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = 1, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = 1, timeUnit = TimeUnit.SECONDS)
open class DynamicConnectivityBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: DCPConstructor = DCPConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128")
    open var workers: Int = 0

    @Param("1", "5", "25")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        scenario = RandomScenarioGenerator().generate(graph, workers, totalSize / workers, 1, readWeight)
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

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = iterations, time = 1, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = warmupIterations, time = 1, timeUnit = TimeUnit.SECONDS)
open class LockElidfsionDynafgmicConngffectivityBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.values()[0]

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: LockElisionDCPConstructor = LockElisionDCPConstructor.values()[0]

    @Param("1", "2", "4", "8", "16", "32", "64", "128")
    open var workers: Int = 0

    @Param("1", "5", "25")
    open var readWeight = 1

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        scenario = RandomScenarioGenerator().generate(graph, workers, totalSize / workers, 1, readWeight)
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

@Throws(RunnerException::class)
fun main() {
    testGraphs()

    val dcpOptions = OptionsBuilder()
        .include(DynamicConnectivityBenchmark::class.java.simpleName)
        //.jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=1", "-Xmx60g", "-Xms5g")
        .jvmArgs("-Xmx60g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("dcp_results.csv")
        .build()
    Runner(dcpOptions).run()

    /*val lockElisionDcpOptions = OptionsBuilder()
        .include(LockElisionDynamicConnectivityBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=10", "-Xmx60g", "-Xms5g")
        //.jvmArgs("-Xmx60g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("dcp_lock_elision_results.csv")
        .build()
    Runner(lockElisionDcpOptions).run()*/
}

fun testGraphs() {
    for (g in GraphParams.values()) {
        val graph = GraphServer.getLookup().graphByParams(g)
        testGraphCorrectness(graph, g.name)
    }
}

fun testGraphCorrectness(graph: Graph, name: String) {
    val n = graph.nodes
    println("Graph $name with $n nodes and ${graph.edges.size} edges")
    for (e in graph.edges) {
        check(e.from() in 0 until n) {
            println("${e.from()} >= $n")
        }
        check(e.to() in 0 until n) {
            println("${e.to()} >= $n")
        }
    }
}