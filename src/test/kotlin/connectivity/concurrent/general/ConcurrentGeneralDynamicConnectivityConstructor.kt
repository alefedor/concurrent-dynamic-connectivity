package connectivity.concurrent.general

import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.concurrent.general.major_coarse_grained.MajorCoarseGrainedDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity

enum class ConcurrentGeneralDynamicConnectivityConstructor(val construct: (size: Int) -> DynamicConnectivity) {
    CoarseGrainedLockingDynamicConnectivity(::CoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDynamicConnectivity(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    FineGrainedLockingDynamicConnectivity(::FineGrainedLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    NBReadsCoarseGrainedLockingDynamicConnectivity(::NBReadsCoarseGrainedLockingDynamicConnectivity),
    NBReadsFineGrainedLockingDynamicConnectivity(::NBReadsFineGrainedLockingDynamicConnectivity),
    MajorDynamicConnectivity(::MajorDynamicConnectivity),
    MajorCoarseGrainedDynamicConnectivity(::MajorCoarseGrainedDynamicConnectivity),
    FCDynamicConnectivity( { size -> thirdparty.Aksenov239.fc.FCDynamicGraphFlush(size, 3) })
}

var globalDcpConstructor: ConcurrentGeneralDynamicConnectivityConstructor = ConcurrentGeneralDynamicConnectivityConstructor.values()[0]