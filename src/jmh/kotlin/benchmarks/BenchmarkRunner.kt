package benchmarks

import benchmarks.util.*
import benchmarks.util.executors.ScenarioExecutor
import benchmarks.util.executors.SuccessiveScenarioExecutor
import benchmarks.util.generators.IncrementalScenarioGenerator
import benchmarks.util.generators.RandomScenarioGenerator
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.results.format.ResultFormatType
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.RunnerException
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

const val iterations = 5
const val warmupIterations = 2
const val TIME_IN_SECONDS = 3

@Throws(RunnerException::class)
fun main() {
    val phaser = Phaser(2)
    thread {
        GraphServer.main(emptyArray())
        phaser.arrive()
    }
    phaser.arriveAndAwaitAdvance()
    Thread.sleep(5000) // wait in case graph server needs internal initialization

    testGraphs()

    val dcpOptions = OptionsBuilder()
        .include(CommonDynamicConnectivityRandomBenchmark::class.java.simpleName)
        .jvmArgs("-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("random_dcp_results.csv")
        .build()
    Runner(dcpOptions).run()

    val lockElisionDcpOptions = OptionsBuilder()
        .include(LockElisionDynamicConnectivityRandomBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=5", "-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("random_dcp_lock_elision_results.csv")
        .build()
    Runner(lockElisionDcpOptions).run()

    val incrementalDcpOptions = OptionsBuilder()
        .include(CommonDynamicConnectivityIncrementalBenchmark::class.java.simpleName)
        .jvmArgs("-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("incremental_dcp_results.csv")
        .build()
    Runner(incrementalDcpOptions).run()

    val incrementalLockElisionDcpOptions = OptionsBuilder()
        .include(LockElisionDynamicConnectivityIncrementalBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=5", "-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("incremental_dcp_lock_elision_results.csv")
        .build()
    Runner(incrementalLockElisionDcpOptions).run()

    val decrementalDcpOptions = OptionsBuilder()
        .include(CommonDynamicConnectivityDecrementalBenchmark::class.java.simpleName)
        .jvmArgs("-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("decremental_dcp_results.csv")
        .build()
    Runner(decrementalDcpOptions).run()

    val decrementalLockElisionDcpOptions = OptionsBuilder()
        .include(LockElisionDynamicConnectivityDecrementalBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=5", "-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("decremental_dcp_lock_elision_results.csv")
        .build()
    Runner(decrementalLockElisionDcpOptions).run()

    val twoLevelDcpOptions = OptionsBuilder()
        .include(CommonDynamicConnectivityTwoLevelBenchmark::class.java.simpleName)
        .jvmArgs("-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("two_level_dcp_results.csv")
        .build()
    Runner(twoLevelDcpOptions).run()

    val twoLevelLockElisionDcpOptions = OptionsBuilder()
        .include(LockElisionDynamicConnectivityTwoLevelBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=5", "-Xmx50g", "-Xms5g")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("two_level_dcp_lock_elision_results.csv")
        .build()
    Runner(twoLevelLockElisionDcpOptions).run()
    GraphServer.close()
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