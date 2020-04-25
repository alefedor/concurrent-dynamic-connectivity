package benchmarks.util

import connectivity.concurrent.general.*
import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity

enum class DCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    CoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDCP(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    ImprovedCoarseGrainedLockingDCP(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    FineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    ImprovedFineGrainedLockingDynamicConnectivity(::ImprovedFineGrainedLockingDynamicConnectivity),
    MajorDynamicConnectivity(::MajorDynamicConnectivity),
}

enum class LockElisionDCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    LockElisionCoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    LockElisionImprovedCoarseGrainedLockingDCP(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    LockElisionFineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    LockElisionImprovedFineGrainedLockingDynamicConnectivity(::ImprovedFineGrainedLockingDynamicConnectivity),
    LockElisionMajorDynamicConnectivity(::MajorDynamicConnectivity)
}