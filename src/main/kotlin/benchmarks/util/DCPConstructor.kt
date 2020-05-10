package benchmarks.util

import connectivity.concurrent.general.*
import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity

enum class DCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    /*CoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDCP(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    NBReadsCoarseGrainedLockingDCP(::NBReadsCoarseGrainedLockingDynamicConnectivity),
    FineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    NBReadsFineGrainedLockingDynamicConnectivity(::NBReadsFineGrainedLockingDynamicConnectivity),*/
    MajorDynamicConnectivity(::MajorDynamicConnectivity),
}

enum class LockElisionDCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    LockElisionCoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    LockElisionImprovedCoarseGrainedLockingDCP(::NBReadsCoarseGrainedLockingDynamicConnectivity),
    LockElisionFineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    LockElisionNBReadsFineGrainedLockingDynamicConnectivity(::NBReadsFineGrainedLockingDynamicConnectivity),
    LockElisionMajorDynamicConnectivity(::MajorDynamicConnectivity)
}