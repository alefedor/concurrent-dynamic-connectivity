package connectivity.concurrent.general

import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity

enum class ConcurrentGeneralDynamicConnectivityConstructor(val construct: (size: Int) -> DynamicConnectivity) {
    /*CoarseGrainedLockingDynamicConnectivity(::CoarseGrainedLockingDynamicConnectivity),
    ImprovedCoarseGrainedLockingDynamicConnectivity(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDynamicConnectivity(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    FineGrainedLockingDynamicConnectivity(::FineGrainedLockingDynamicConnectivity),
    SFineGrainedLockingDynamicConnectivity(::SFineGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteFairLockingDynamicConnectivity(::CoarseGrainedReadWriteFairLockingDynamicConnectivity),*/
    FineGrainedFairLockingDynamicConnectivity(::FineGrainedFairLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    ImprovedFineGrainedLockingDynamicConnectivity(::ImprovedFineGrainedLockingDynamicConnectivity),
    MajorDynamicConnectivity(::MajorDynamicConnectivity)
}

var globalDcpConstructor: ConcurrentGeneralDynamicConnectivityConstructor = ConcurrentGeneralDynamicConnectivityConstructor.MajorDynamicConnectivity