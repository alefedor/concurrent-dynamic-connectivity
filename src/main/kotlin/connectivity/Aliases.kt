package connectivity

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.jctools.maps.NonBlockingHashMap
import thirdparty.boundary.NonBlockingHashSetLong
import thirdparty.jctools.NonBlockingHashMapLong


typealias Edge = Long
typealias SequentialEdgeMap<T> = Long2ReferenceOpenHashMap<T>
typealias ConcurrentEdgeMap<T> = NonBlockingHashMapLong<T>
typealias SequentialEdgeSet = LongOpenHashSet
typealias ConcurrentEdgeSet = NonBlockingHashSetLong

// -1 is used instead of null to avoid boxing
internal const val NO_EDGE: Long = -1L