package benchmarks.util

import connectivity.concurrent.general.*
import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.concurrent.general.major_coarse_grained.MajorCoarseGrainedDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity
import thirdparty.Aksenov239.fc.FCClassicDynamicGraph
import thirdparty.Aksenov239.fc.FCClassicDynamicGraphFlush
import thirdparty.Aksenov239.fc.FCDynamicGraph
import thirdparty.Aksenov239.fc.FCDynamicGraphFlush

enum class DCPConstructor(val construct: (Int, Int) -> DynamicConnectivity) {
    /*CoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDCP(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    NBReadsCoarseGrainedLockingDCP(::NBReadsCoarseGrainedLockingDynamicConnectivity),
    FineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    NBReadsFineGrainedLockingDynamicConnectivity(::NBReadsFineGrainedLockingDynamicConnectivity),*/
    //MajorDynamicConnectivity(::MajorDynamicConnectivity),
    //MajorCoarseGrainedDynamicConnectivity(::MajorCoarseGrainedDynamicConnectivity),
    FCReadOptimizedDynamicConnectivity(::FCDynamicGraph),
    FCDynamicConnectivity(::FCClassicDynamicGraph),
}

enum class LockElisionDCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    LockElisionCoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    LockElisionImprovedCoarseGrainedLockingDCP(::NBReadsCoarseGrainedLockingDynamicConnectivity),
    LockElisionFineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    LockElisionNBReadsFineGrainedLockingDynamicConnectivity(::NBReadsFineGrainedLockingDynamicConnectivity),
    LockElisionMajorDynamicConnectivity(::MajorDynamicConnectivity),
    LockElisionMajorCoarseGrainedDynamicConnectivity(::MajorCoarseGrainedDynamicConnectivity),
}