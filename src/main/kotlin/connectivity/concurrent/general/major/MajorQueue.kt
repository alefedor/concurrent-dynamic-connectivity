package connectivity.concurrent.general.major

import connectivity.Edge
import connectivity.NO_EDGE
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

// single consumer, multiple producer queue
class MajorQueue {
    private val dummy = MajorQueueNode()
    @Volatile
    private var head = dummy // no need in AtomicReference, because there is only one thread that can read
    private val tail: AtomicRef<MajorQueueNode> = atomic(dummy)

    fun pop(): Edge {
        val r = head.next.value ?: return NO_EDGE
        head = r.node
        return r.edge
    }

    fun push(value: Edge) { // Michael-Scott implementation
        val newNode = MajorQueueNode()
        val newNext = MajorQueueContent(value, newNode)
        while (true) {
            val currentTail = tail.value
            val tailNext = currentTail.next.value

            if (currentTail == tail.value) {
                if (tailNext == null) {
                    if (currentTail.next.compareAndSet(null, newNext)) {
                        tail.compareAndSet(currentTail, newNode)
                        return
                    }
                } else {
                    tail.compareAndSet(currentTail, tailNext.node)
                }
            }
        }
    }

    fun isNotEmpty() = head.next.value != null
}

private class MajorQueueNode {
    val next: AtomicRef<MajorQueueContent?> = atomic(null)
}

private class MajorQueueContent(val edge: Long, val node: MajorQueueNode)
