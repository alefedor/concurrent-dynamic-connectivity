package connectivity.concurrent.general

import connectivity.sequential.general.DynamicConnectivity
import connectivity.sequential.general.SequentialDynamicConnectivity
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CoarseGrainedReadWriteFairLockingDynamicConnectivity(size: Int) : DynamicConnectivity {
    private val lock = ReentrantReadWriteLock(true)
    private val connectivity = SequentialDynamicConnectivity(size)

    override fun addEdge(u: Int, v: Int) = lock.write {
        connectivity.addEdge(u, v)
    }

    @Synchronized
    override fun removeEdge(u: Int, v: Int) = lock.write {
        connectivity.removeEdge(u, v)
    }

    @Synchronized
    override fun connected(u: Int, v: Int): Boolean = lock.read {
        connectivity.connected(u, v)
    }
}