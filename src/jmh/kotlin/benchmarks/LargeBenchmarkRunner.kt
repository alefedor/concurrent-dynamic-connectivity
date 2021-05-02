package benchmarks

import benchmarks.util.Graph
import org.openjdk.jmh.results.format.ResultFormatType
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.RunnerException
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.concurrent.Phaser
import kotlin.concurrent.thread

@Throws(RunnerException::class)
fun main() {
    val phaser = Phaser(2)
    thread {
        LargeGraphServer.main(emptyArray())
        phaser.arrive()
    }
    phaser.arriveAndAwaitAdvance()
    Thread.sleep(5000) // wait in case graph server needs internal initialization

    val dcpOptions = OptionsBuilder()
        .include(LargeCommonDynamicConnectivityRandomBenchmark::class.java.simpleName)
        .jvmArgs("-Xmx250g", "-Xms150g", "-XX:+UseNUMA")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("large_random_dcp_results.csv")
        .build()
    Runner(dcpOptions).run()

    val lockElisionDcpOptions = OptionsBuilder()
        .include(LargeLockElisionDynamicConnectivityRandomBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=5", "-Xmx250g", "-Xms150g", "-XX:+UseNUMA")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("large_random_dcp_lock_elision_results.csv")
        .build()
    Runner(lockElisionDcpOptions).run()

    val incrementalDcpOptions = OptionsBuilder()
        .include(LargeCommonDynamicConnectivityIncrementalBenchmark::class.java.simpleName)
        .jvmArgs("-Xmx250g", "-Xms150g", "-XX:+UseNUMA")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("large_incremental_dcp_results.csv")
        .build()
    Runner(incrementalDcpOptions).run()

    val incrementalLockElisionDcpOptions = OptionsBuilder()
        .include(LargeLockElisionDynamicConnectivityIncrementalBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=5", "-Xmx250g", "-Xms150g", "-XX:+UseNUMA")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("large_incremental_dcp_lock_elision_results.csv")
        .build()
    Runner(incrementalLockElisionDcpOptions).run()

    val decrementalDcpOptions = OptionsBuilder()
        .include(LargeCommonDynamicConnectivityDecrementalBenchmark::class.java.simpleName)
        .jvmArgs("-Xmx250g", "-Xms150g", "-XX:+UseNUMA")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("large_decremental_dcp_results.csv")
        .build()
    Runner(decrementalDcpOptions).run()

    val decrementalLockElisionDcpOptions = OptionsBuilder()
        .include(LargeLockElisionDynamicConnectivityDecrementalBenchmark::class.java.simpleName)
        .jvmArgs("-XX:+UseRTMLocking", "-XX:RTMRetryCount=5", "-Xmx250g", "-Xms150g", "-XX:+UseNUMA")
        .forks(1)
        .resultFormat(ResultFormatType.CSV)
        .result("large_decremental_dcp_lock_elision_results.csv")
        .build()
    Runner(decrementalLockElisionDcpOptions).run()

    LargeGraphServer.close()
}