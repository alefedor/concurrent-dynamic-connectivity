package benchmarks

import benchmarks.util.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.results.format.ResultFormatType
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.RunnerException
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
open class DynamicConnectivityBenchmark {
    @Param
    open var graphParams: GraphParams = GraphParams.INTERNET_TOPOLOGY

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: DCPConstructor = DCPConstructor.CoarseGrainedLockingDCP

    @Param("1", "2", "4", "8", "16", "32", "64")
    open var workers: Int = 0

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        scenario = ScenarioGenerator.generate(graph, workers, 10000000 / workers, 1, 1)
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
@Measurement(iterations = 8, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
open class DynamicConnectivityBenchmarkMoreReads {
    @Param
    open var graphParams: GraphParams = GraphParams.INTERNET_TOPOLOGY

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: DCPConstructor = DCPConstructor.CoarseGrainedLockingDCP

    @Param("1", "2", "4", "8", "16", "32", "64")
    open var workers: Int = 0

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        scenario = ScenarioGenerator.generate(graph, workers, 10000000 / workers, 1, 5)
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
@Measurement(iterations = 8, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
open class DynamicConnectivityBenchmarkMoreMoreReads {
    @Param
    open var graphParams: GraphParams = GraphParams.INTERNET_TOPOLOGY

    lateinit var scenario: Scenario
    lateinit var scenarioExecutor: ScenarioExecutor

    @Param
    open var dcpConstructor: DCPConstructor = DCPConstructor.CoarseGrainedLockingDCP

    @Param("1", "2", "4", "8", "16", "32", "64")
    open var workers: Int = 0

    @Benchmark
    fun benchmark() {
        scenarioExecutor.run()
    }

    @Setup(Level.Trial)
    fun initialize() {
        val graph = GraphServer.getLookup().graphByParams(graphParams)
        scenario = ScenarioGenerator.generate(graph, workers, 10000000 / workers, 1, 25)
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
        //.addProfiler(LinuxPerfAsmProfiler::class.java)
        //.addProfiler(LinuxPerfNormProfiler::class.java)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=10", "-Xmx50g", "-Xms2g")
        //.jvmArgs("-Xmx50g", "-Xms2g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("dcp_results_lock_elision.csv")
        .build()
    Runner(dcpOptions).run()

    val dcpOptionsMoreReads = OptionsBuilder()
        .include(DynamicConnectivityBenchmarkMoreReads::class.java.simpleName)
        //.addProfiler(LinuxPerfAsmProfiler::class.java)
        //.addProfiler(LinuxPerfNormProfiler::class.java)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=10", "-Xmx50g", "-Xms2g")
        //.jvmArgs("-Xmx50g", "-Xms2g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("dcp_results_more_reads_lock_elision.csv")
        .build()

    Runner(dcpOptionsMoreReads).run()

    val dcpOptionsMoreMoreReads = OptionsBuilder()
        .include(DynamicConnectivityBenchmarkMoreMoreReads::class.java.simpleName)
        //.addProfiler(LinuxPerfAsmProfiler::class.java)
        //.addProfiler(LinuxPerfNormProfiler::class.java)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=10", "-Xmx50g", "-Xms2g")
        //.jvmArgs("-Xmx50g", "-Xms2g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("dcp_results_more_more_reads_lock_elision.csv")
        .build()
    Runner(dcpOptionsMoreMoreReads).run()
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