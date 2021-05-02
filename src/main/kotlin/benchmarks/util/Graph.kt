package benchmarks.util

import java.io.Serializable

const val MAX_BITS_PER_NODE = 27
const val NODE_MASK = (1L shl MAX_BITS_PER_NODE) - 1

class Graph(val nodes: Int, val edges: LongArray) : Serializable

inline fun Long.to(): Int {
    return (this and NODE_MASK).toInt()
}

inline fun Long.from(): Int {
    return ((this shr MAX_BITS_PER_NODE) and NODE_MASK).toInt()
}

fun bidirectionalEdge(from: Int, to: Int): Long = to + (from.toLong() shl MAX_BITS_PER_NODE)