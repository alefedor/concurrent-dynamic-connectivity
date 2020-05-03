package connectivity

import kotlin.math.max
import kotlin.math.min

const val BITS = 32
const val MASK = (1L shl BITS) - 1

// getters for endpoints of an edge
inline fun Edge.u(): Int = (this and MASK).toInt()
inline fun Edge.v(): Int = ((this shr BITS) and MASK).toInt()

// constructor for a bidirectional edge
inline fun makeEdge(u: Int, v: Int): Edge = makeDirectedEdge(min(u, v), max(u, v))
// constructor for a directed edge
inline fun makeDirectedEdge(u: Int, v: Int): Edge = u + (v.toLong() shl BITS)

fun upperPowerOfTwo(n: Int): Int {
    var result = 0
    while (n > (1 shl result))
        result++
    return result
}