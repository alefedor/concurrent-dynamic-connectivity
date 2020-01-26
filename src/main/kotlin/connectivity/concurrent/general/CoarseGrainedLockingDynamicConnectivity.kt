package connectivity.concurrent.general

import connectivity.sequential.general.DynamicConnectivity
import connectivity.sequential.general.SequentialDynamicConnectivity

class CoarseGrainedLockingDynamicConnectivity(size: Int) : DynamicConnectivity {
    private val connectivity = SequentialDynamicConnectivity(size)

    @Synchronized
    override fun addEdge(u: Int, v: Int) = connectivity.addEdge(u, v)

    @Synchronized
    override fun removeEdge(u: Int, v: Int) = connectivity.removeEdge(u, v)

    @Synchronized
    override fun connected(u: Int, v: Int): Boolean = connectivity.connected(u, v)
}