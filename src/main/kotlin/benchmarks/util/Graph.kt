package benchmarks.util

import java.io.Serializable

class Graph(val nodes: Int, val edges: LongArray) : Serializable

inline fun Long.to(): Int {
    return (this and (16777215)).toInt()
}

inline fun Long.from(): Int {
    return ((this shr 24) and 16777215).toInt()
}

fun bidirectionalEdge(from: Int, to: Int): Long {
    return from + (to.toLong() shl 24)
}