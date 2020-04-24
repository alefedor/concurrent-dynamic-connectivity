package connectivity.concurrent.general.major_faster

import connectivity.Edge
import connectivity.NO_EDGE
import java.util.concurrent.atomic.AtomicReference

class MajorQueue {
    private val dummy = MajorQueueNode(AtomicReference())
    @Volatile
    private var head = dummy // no need in AtomicReference, because there is only one thread that can read
    private var tail: AtomicReference<MajorQueueNode> = AtomicReference(dummy)

    fun pop(): Edge {
        val r = head.next.get() ?: return NO_EDGE
        head = r.node
        return r.edge
    }

    fun push(value: Edge) { // Michael-Scott implementation
        val newNode = MajorQueueNode(AtomicReference())
        val newNext = MajorQueueContent(value, newNode)
        while (true) {
            val currentTail = tail.get()
            val tailNext = currentTail.next.get()

            if (currentTail == tail.get()) {
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

    fun isNotEmpty() = head.next.get() != null
}

private class MajorQueueNode(val next: AtomicReference<MajorQueueContent>)

private class MajorQueueContent(val edge: Long, val node: MajorQueueNode)
