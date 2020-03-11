package benchmarks.util

import connectivity.concurrent.general.*
import connectivity.sequential.general.DynamicConnectivity

enum class DCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    //CoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    //ImprovedCoarseGrainedLockingDCP(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    //CoarseGrainedReadWriteLockingDCP(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    //FineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    SFineGrainedLockElisionLockingDCP(::SFineGrainedLockingDynamicConnectivity),
    //CoarseGrainedReadWriteFairLockingDynamicConnectivity(::CoarseGrainedReadWriteFairLockingDynamicConnectivity),
    //FineGrainedFairLockingDynamicConnectivity(::FineGrainedFairLockingDynamicConnectivity),
    //FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    ImprovedFineGrainedLockElisionLockingDynamicConnectivity(::ImprovedFineGrainedLockingDynamicConnectivity)
}