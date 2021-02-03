package connectivity.concurrent.general.major

import connectivity.NO_EDGE
import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicLong

class RemovalOperationInfo(val u: Int, val v: Int, val additionalRoot: Node) {
    val replacement = atomic(NO_EDGE)
}