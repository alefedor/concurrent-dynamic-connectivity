package connectivity.concurrent.general.major_faster

import connectivity.NO_EDGE
import java.util.concurrent.atomic.AtomicLong

class RemovalOperationInfo(val u: Int, val v: Int, val additionalRoot: Node) {
    val replacement = AtomicLong(NO_EDGE)
}