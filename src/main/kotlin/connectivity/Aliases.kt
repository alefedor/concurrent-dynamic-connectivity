package connectivity

import org.cliffc.high_scale_lib.NonBlockingHashMap

typealias SequentialEdgeMap<T> = HashMap<Pair<Int, Int>, T>
typealias ConcurrentEdgeMap<T> = NonBlockingHashMap<Pair<Int, Int>, T>
typealias SequentialEdgeSet = HashSet<Pair<Int, Int>>