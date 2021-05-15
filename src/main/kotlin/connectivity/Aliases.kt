package connectivity

import com.google.common.collect.*
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap
import it.unimi.dsi.fastutil.objects.*
import thirdparty.jctools.NonBlockingHashMapLong

typealias Edge = Long
typealias SequentialEdgeMap<T> = Long2ReferenceOpenHashMap<T>
typealias ConcurrentEdgeMap<T> = NonBlockingHashMapLong<T>
// both sets are not optimized for Long due to fairness reasons
typealias SequentialEdgeSet = ObjectOpenHashSet<Long>
typealias ConcurrentEdgeSet = ConcurrentHashMultiset<Long>

// -1 is used instead of null to avoid boxing
internal const val NO_EDGE: Long = -1L
internal const val CLOSED: Long = -2L
internal const val INITIAL_SIZE = 8
const val SAMPLING_TRIES = 12L