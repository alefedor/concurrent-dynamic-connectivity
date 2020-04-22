package connectivity

import kotlin.math.max
import kotlin.math.min

// getters for endpoints of an edge
inline fun Edge.u(): Int = (this and (16777215)).toInt()
inline fun Edge.v(): Int = ((this shr 24) and 16777215).toInt()

// constructor for a bidirectional edge
inline fun makeEdge(u: Int, v: Int): Edge = min(u, v) + (max(u, v).toLong() shl 24)
// constructor for a directed edge
inline fun makeDirectedEdge(u: Int, v: Int): Edge = u + (v.toLong() shl 24)