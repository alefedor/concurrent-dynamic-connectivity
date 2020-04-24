package connectivity

import com.boundary.high_scale_lib.NonBlockingHashSetLong
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.cliffc.high_scale_lib.NonBlockingHashMapLong


typealias Edge = Long
typealias SequentialEdgeMap<T> = Long2ReferenceOpenHashMap<T>
typealias ConcurrentEdgeMap<T> = NonBlockingHashMapLong<T>
typealias SequentialEdgeSet = LongOpenHashSet
typealias ConcurrentEdgeSet = NonBlockingHashSetLong

// -1 is used instead of null to avoid boxing
internal const val NO_EDGE: Long = -1L