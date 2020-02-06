package benchmarks.util

import connectivity.concurrent.general.CoarseGrainedLockingDynamicConnectivity
import connectivity.concurrent.general.CoarseGrainedReadWriteLockingDynamicConnectivity
import connectivity.concurrent.general.ImprovedCoarseGrainedLockingDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity

enum class DCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    CoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    ImprovedCoarseGrainedLockingDCP(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDCP(::CoarseGrainedReadWriteLockingDynamicConnectivity),
}