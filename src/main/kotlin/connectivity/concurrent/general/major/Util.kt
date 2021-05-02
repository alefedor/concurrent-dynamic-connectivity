package connectivity.concurrent.general.major

import benchmarks.util.MAX_BITS_PER_NODE
import java.util.concurrent.*

typealias EdgeState = Int

const val INITIAL = 0
const val SPANNING = 1
const val NON_SPANNING = 2
const val SPANNING_IN_PROGRESS = 3
const val BITS_FOR_STATUS = 2
const val BITS_FOR_ID = 63 - BITS_FOR_STATUS - 2 * MAX_BITS_PER_NODE
const val BITS_FOR_STATE = BITS_FOR_STATUS + BITS_FOR_ID

inline fun EdgeState.status() = this and ((1 shl BITS_FOR_STATUS) - 1)
inline fun EdgeState.rank() = this shr BITS_FOR_STATUS

inline fun makeState(status: Int, rank: Int) = status + (rank shl BITS_FOR_STATUS)
inline fun randomBits() = ThreadLocalRandom.current().nextInt(1 shl BITS_FOR_ID)


inline fun pack(state: Int, edge: Long): Long = state + (edge shl BITS_FOR_STATE)
inline fun Long.edge(): Long = this shr BITS_FOR_STATE
inline fun Long.state(): Int = (this and ((1L shl BITS_FOR_STATE) - 1)).toInt()