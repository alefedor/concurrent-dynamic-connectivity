package benchmarks.util

class BenchmarkThread(val threadId: Int, target: () -> Unit) : Thread(target)