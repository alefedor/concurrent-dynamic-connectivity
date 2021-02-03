package benchmarks.util

import connectivity.concurrent.general.*
import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.concurrent.general.major_coarse_grained.MajorCoarseGrainedDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity
import thirdparty.Aksenov239.fc.FCDynamicGraph

enum class DCPConstructor(val construct: (Int, Int) -> DynamicConnectivity) {
    /*NBReadsCoarseGrainedLockingDCP(addTrivialParameter(::NBReadsCoarseGrainedLockingDynamicConnectivity)),
    NBReadsFineGrainedLockingDynamicConnectivity(addTrivialParameter(::NBReadsFineGrainedLockingDynamicConnectivity)),*/
    MajorDynamicConnectivity(addTrivialParameter(::MajorDynamicConnectivity)),
    /*MajorCoarseGrainedDynamicConnectivity(addTrivialParameter(::MajorCoarseGrainedDynamicConnectivity)),
    FCReadOptimizedDynamicConnectivity(::FCDynamicGraph),*/
    CoarseGrainedLockingDCP(addTrivialParameter(::CoarseGrainedLockingDynamicConnectivity)),/*
    CoarseGrainedReadWriteLockingDCP(addTrivialParameter(::CoarseGrainedReadWriteLockingDynamicConnectivity)),
    FineGrainedLockingDCP(addTrivialParameter(::FineGrainedLockingDynamicConnectivity)),
    FineGrainedReadWriteLockingDynamicConnectivity(addTrivialParameter(::FineGrainedReadWriteLockingDynamicConnectivity)),*/
}

enum class LockElisionDCPConstructor(val construct: (Int) -> DynamicConnectivity) {
    LockElisionCoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    LockElisionNBReadsCoarseGrainedLockingDCP(::NBReadsCoarseGrainedLockingDynamicConnectivity),
    //LockElisionFineGrainedLockingDCP(::FineGrainedLockingDynamicConnectivity),
    //LockElisionNBReadsFineGrainedLockingDynamicConnectivity(::NBReadsFineGrainedLockingDynamicConnectivity),
    LockElisionMajorDynamicConnectivity(::MajorDynamicConnectivity),
    LockElisionMajorCoarseGrainedDynamicConnectivity(::MajorCoarseGrainedDynamicConnectivity),
}

enum class DCPForModificationsConstructor(val construct: (Int, Int) -> DynamicConnectivity) {
    CoarseGrainedLockingDCP(addTrivialParameter(::CoarseGrainedLockingDynamicConnectivity)),
    FineGrainedLockingDCP(addTrivialParameter(::FineGrainedLockingDynamicConnectivity)),
    MajorDynamicConnectivity(addTrivialParameter(::MajorDynamicConnectivity)),
    MajorCoarseGrainedDynamicConnectivity(addTrivialParameter(::MajorCoarseGrainedDynamicConnectivity)),
    FCReadOptimizedDynamicConnectivity(::FCDynamicGraph),
}

enum class LockElisionDCPForModificationsConstructor(val construct: (Int) -> DynamicConnectivity) {
    LockElisionCoarseGrainedLockingDCP(::CoarseGrainedLockingDynamicConnectivity),
    LockElisionMajorDynamicConnectivity(::MajorDynamicConnectivity),
    LockElisionMajorCoarseGrainedDynamicConnectivity(::MajorCoarseGrainedDynamicConnectivity),
}

inline fun <T> addTrivialParameter(crossinline f: (Int) -> T): (Int, Int) -> T = { size, threads -> f(size) }