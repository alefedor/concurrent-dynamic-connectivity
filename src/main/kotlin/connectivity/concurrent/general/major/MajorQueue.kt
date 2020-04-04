package connectivity.concurrent.general.major

import java.util.concurrent.atomic.AtomicReference

class MajorQueue<T> {
    private val dummy = MajorQueueNode<T>(AtomicReference())
    @Volatile
    private var head = dummy // no need in AtomicReference, because there is only one thread that can read
    private var tail: AtomicReference<MajorQueueNode<T>> = AtomicReference(dummy)

    fun pop(): T? {
        val r = head.next.get() ?: return null
        head = r.second
        return r.first
    }

    fun push(value: T) { // Michael-Scott implementation
        val newNode = MajorQueueNode<T>(AtomicReference())
        val newNext = Pair(value, newNode)
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
                    tail.compareAndSet(currentTail, tailNext.second)
                }
            }
        }
    }

    fun isNotEmpty() = head.next.get() != null
}

class MajorQueueNode<T>(val next: AtomicReference<Pair<T, MajorQueueNode<T>>>)