package connectivity

import kotlin.math.max
import kotlin.math.min

inline fun Long.u(): Int = (this and (16777215)).toInt()

inline fun Long.v(): Int = ((this shr 24) and 16777215).toInt()

inline fun makeEdge(u: Int, v: Int): Long = min(u, v) + (max(u, v).toLong() shl 24)