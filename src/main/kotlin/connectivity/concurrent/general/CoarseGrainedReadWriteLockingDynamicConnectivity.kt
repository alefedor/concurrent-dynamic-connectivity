package connectivity.concurrent.general

import connectivity.sequential.general.DynamicConnectivity
import connectivity.sequential.general.SequentialDynamicConnectivity
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.locks.StampedLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CoarseGrainedReadWriteLockingDynamicConnectivity(size: Int) : DynamicConnectivity {
    private val lock = StampedLock()
    private val connectivity = SequentialDynamicConnectivity(size)

    override fun addEdge(u: Int, v: Int) {
        val stamp = lock.writeLock()
        try {
            connectivity.addEdge(u, v)
        } finally {
            lock.unlockWrite(stamp)
        }
    }

    override fun removeEdge(u: Int, v: Int)  {
        val stamp = lock.writeLock()
        try {
            connectivity.removeEdge(u, v)
        } finally {
            lock.unlockWrite(stamp)
        }
    }

    override fun connected(u: Int, v: Int): Boolean {
        val stamp = lock.readLock()
        try {
            return connectivity.connected(u, v)
        } finally {
            lock.unlockRead(stamp)
        }
    }
}