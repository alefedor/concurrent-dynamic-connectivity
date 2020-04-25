package connectivity.concurrent.general

import connectivity.concurrent.general.major.MajorDynamicConnectivity
import connectivity.concurrent.general.major_faster.MajorFasterDynamicConnectivity
import connectivity.sequential.general.DynamicConnectivity

enum class ConcurrentGeneralDynamicConnectivityConstructor(val construct: (size: Int) -> DynamicConnectivity) {
    CoarseGrainedLockingDynamicConnectivity(::CoarseGrainedLockingDynamicConnectivity),
    CoarseGrainedReadWriteLockingDynamicConnectivity(::CoarseGrainedReadWriteLockingDynamicConnectivity),
    FineGrainedLockingDynamicConnectivity(::FineGrainedLockingDynamicConnectivity),
    FineGrainedReadWriteLockingDynamicConnectivity(::FineGrainedReadWriteLockingDynamicConnectivity),
    ImprovedCoarseGrainedLockingDynamicConnectivity(::ImprovedCoarseGrainedLockingDynamicConnectivity),
    ImprovedFineGrainedLockingDynamicConnectivity(::ImprovedFineGrainedLockingDynamicConnectivity),
    MajorDynamicConnectivity(::MajorDynamicConnectivity),
    //MajorFasterDynamicConnectivity(::MajorFasterDynamicConnectivity)
}

var globalDcpConstructor: ConcurrentGeneralDynamicConnectivityConstructor = ConcurrentGeneralDynamicConnectivityConstructor.MajorDynamicConnectivity