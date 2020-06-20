package connectivity.concurrent.general.major

typealias EdgeState = Int

const val INITIAL = 0
const val SPANNING = 1
const val NON_SPANNING = 2
const val REPLACEMENT = 3
const val REMOVED = 4

const val bitsForStatus = 3

inline fun EdgeState.status() = this and ((1 shl bitsForStatus) - 1)
inline fun EdgeState.rank() = this shr bitsForStatus

inline fun makeState(status: Int, rank: Int) = status + (rank shl bitsForStatus)