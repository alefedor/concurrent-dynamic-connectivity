package benchmarks.util

import connectivity.concurrent.general.*
import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.concurrent.general.major_coarse_grained.MajorCoarseGrainedDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity
import thirdparty.Aksenov239.fc.*
import java.util.concurrent.atomic.*

enum class DCPConstructor {
    FineGrainedLockingDCP,
    FineGrainedReadWriteLockingDynamicConnectivity,
    MajorDynamicConnectivity,
    NBFCDynamicConnectivity,
    NBReadsCoarseGrainedLockingDCP,
    NBReadsFineGrainedLockingDynamicConnectivity,
    MajorCoarseGrainedDynamicConnectivity,
    FCReadOptimizedDynamicConnectivity,
    CoarseGrainedLockingDCP,
    CoarseGrainedReadWriteLockingDCP
}

// JMH annotation processor fails when a lambda is in enum
fun DCPConstructor.constructor(): (Int, Int) -> DynamicConnectivity = when(this) {
    DCPConstructor.NBReadsCoarseGrainedLockingDCP -> addTrivialParameter(::NBReadsCoarseGrainedLockingDynamicConnectivity)
    DCPConstructor.NBReadsFineGrainedLockingDynamicConnectivity -> addTrivialParameter(::NBReadsFineGrainedLockingDynamicConnectivity)
    DCPConstructor.MajorDynamicConnectivity -> addTrivialParameter(::MajorDynamicConnectivity)
    DCPConstructor.MajorCoarseGrainedDynamicConnectivity -> addTrivialParameter(::MajorCoarseGrainedDynamicConnectivity)
    DCPConstructor.FCReadOptimizedDynamicConnectivity -> ::FCDynamicGraph
    DCPConstructor.NBFCDynamicConnectivity -> ::FCNBReadsGraph
    DCPConstructor.CoarseGrainedLockingDCP -> addTrivialParameter(::CoarseGrainedLockingDynamicConnectivity)
    DCPConstructor.CoarseGrainedReadWriteLockingDCP -> addTrivialParameter(::CoarseGrainedReadWriteLockingDynamicConnectivity)
    DCPConstructor.FineGrainedLockingDCP -> addTrivialParameter(::FineGrainedLockingDynamicConnectivity)
    DCPConstructor.FineGrainedReadWriteLockingDynamicConnectivity -> addTrivialParameter(::FineGrainedReadWriteLockingDynamicConnectivity)
}

enum class LockElisionDCPConstructor {
    LockElisionCoarseGrainedLockingDCP(),
    LockElisionNBReadsCoarseGrainedLockingDCP(),
    LockElisionMajorCoarseGrainedDynamicConnectivity(),
}

fun LockElisionDCPConstructor.constructor(): (Int) -> DynamicConnectivity = when(this) {
    LockElisionDCPConstructor.LockElisionCoarseGrainedLockingDCP -> ::CoarseGrainedLockingDynamicConnectivity
    LockElisionDCPConstructor.LockElisionNBReadsCoarseGrainedLockingDCP -> ::NBReadsCoarseGrainedLockingDynamicConnectivity
    LockElisionDCPConstructor.LockElisionMajorCoarseGrainedDynamicConnectivity -> ::MajorCoarseGrainedDynamicConnectivity
}

enum class DCPForModificationsConstructor {
    CoarseGrainedLockingDCP(),
    FineGrainedLockingDCP(),
    MajorDynamicConnectivity(),
    MajorCoarseGrainedDynamicConnectivity(),
    FCReadOptimizedDynamicConnectivity(),
}

fun DCPForModificationsConstructor.constructor(): (Int, Int) -> DynamicConnectivity = when(this) {
    DCPForModificationsConstructor.CoarseGrainedLockingDCP -> addTrivialParameter(::CoarseGrainedLockingDynamicConnectivity)
    DCPForModificationsConstructor.FineGrainedLockingDCP -> addTrivialParameter(::FineGrainedLockingDynamicConnectivity)
    DCPForModificationsConstructor.MajorDynamicConnectivity -> addTrivialParameter(::MajorDynamicConnectivity)
    DCPForModificationsConstructor.MajorCoarseGrainedDynamicConnectivity -> addTrivialParameter(::MajorCoarseGrainedDynamicConnectivity)
    DCPForModificationsConstructor.FCReadOptimizedDynamicConnectivity -> ::FCDynamicGraph
}


enum class LockElisionDCPForModificationsConstructor() {
    LockElisionCoarseGrainedLockingDCP(),
    LockElisionMajorCoarseGrainedDynamicConnectivity(),
}

fun LockElisionDCPForModificationsConstructor.constructor(): (Int) -> DynamicConnectivity = when(this) {
    LockElisionDCPForModificationsConstructor.LockElisionCoarseGrainedLockingDCP -> ::CoarseGrainedLockingDynamicConnectivity
    LockElisionDCPForModificationsConstructor.LockElisionMajorCoarseGrainedDynamicConnectivity -> ::MajorCoarseGrainedDynamicConnectivity
}


inline fun <T> addTrivialParameter(crossinline f: (Int) -> T): (Int, Int) -> T = { size, threads -> f(size) }