package connectivity.concurrent.general

import connectivity.concurrent.tree.ConcurrentEulerTourTree
import connectivity.concurrent.tree.Node
import connectivity.concurrent.tree.recalculate
import connectivity.concurrent.tree.update
import connectivity.sequential.general.DynamicConnectivity
import org.cliffc.high_scale_lib.NonBlockingHashMap
import java.lang.IllegalStateException
import kotlin.collections.HashMap
import kotlin.math.max
import kotlin.math.min

class ImprovedFineGrainedLockingDynamicConnectivity(private val size: Int) : DynamicConnectivity {
    private val levels: Array<ConcurrentEulerTourTree>
    private val ranks = NonBlockingHashMap<Pair<Int, Int>, Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < size) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { ConcurrentEulerTourTree(size) }
    }

    override fun addEdge(u: Int, v: Int) {
        lockComponents(u, v) {
            val edge = Pair(min(u, v), max(u, v))
            ranks[edge] = 0
            if (!levels[0].connectedSimple(u, v, null)) {
                levels[0].addEdge(u, v)
            } else {
                levels[0].node(u).update {
                    nonTreeEdges.add(edge)
                }
                levels[0].node(v).update {
                    nonTreeEdges.add(edge)
                }
            }
        }
    }

    override fun removeEdge(u: Int, v: Int) {
        lockComponents(u, v) {
            val edge = Pair(min(u, v), max(u, v))
            val rank = ranks[edge] ?: throw IllegalStateException()
            ranks.remove(edge)
            val level = levels[rank]
            val isNonTreeEdge = level.node(u).nonTreeEdges.contains(edge)

            if (isNonTreeEdge) {
                level.node(u).update {
                    nonTreeEdges.remove(edge)
                }
                level.node(v).update {
                    nonTreeEdges.remove(edge)
                }
                return
            }
            for (r in rank downTo 0) {
                var (uRoot, vRoot) = levels[r].removeEdge(u, v, false)

                if (uRoot.size > vRoot.size) {
                    val tmp = uRoot
                    uRoot = vRoot
                    vRoot = tmp
                }

                val lowerRoot = if (uRoot.parent != null) uRoot else vRoot

                // promote tree edges for less component
                increaseTreeEdgesRank(uRoot, u, v, r)
                val replacementEdge = findReplacement(uRoot, r, lowerRoot)
                if (replacementEdge != null) {
                    for (i in r downTo 0) {
                        val lr = if (i == r) {
                            lowerRoot
                        } else {
                            val (ur, vr) = levels[i].removeEdge(u, v, false)
                            if (ur.parent != null) ur else vr
                        }

                        levels[i].addEdge(replacementEdge.first, replacementEdge.second, i == r, lr)
                    }
                    break
                } else {
                    // linearization point, do an actual split on this level
                    uRoot.parent = null
                    vRoot.parent = null
                    uRoot.version.inc()
                    vRoot.version.inc()
                }
            }
        }

    }

    override fun connected(u: Int, v: Int) = levels[0].connected(u, v)

    private fun increaseTreeEdgesRank(node: Node, u: Int, v: Int, rank: Int) {
        if (!node.hasCurrentLevelTreeEdges) return

        node.currentLevelTreeEdge?.let {
            node.currentLevelTreeEdge = null
            if (it.first < it.second) { // not to promote the same edge twice
                levels[rank + 1].addEdge(it.first, it.second)
                ranks[it] = rank + 1
            }
        }

        node.left?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        node.right?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        node.recalculate()
    }

    private fun findReplacement(node: Node, rank: Int, additionalRoot: Node): Pair<Int, Int>? {
        if (!node.hasNonTreeEdges) return null

        val iterator = node.nonTreeEdges.iterator()

        var result: Pair<Int, Int>? = null

        while (iterator.hasNext()) {
            val edge = iterator.next()
            val firstNode = levels[rank].node(edge.first)
            if (firstNode != node)
                firstNode.update {
                    nonTreeEdges.remove(edge)
                }
            else
                levels[rank].node(edge.second).update {
                    nonTreeEdges.remove(edge)
                }
            iterator.remove()

            if (!levels[rank].connectedSimple(edge.first, edge.second, additionalRoot)) {
                // is replacement
                result = edge
                break
            } else {
                // promote non-tree edge
                levels[rank + 1].node(edge.first).update {
                    nonTreeEdges.add(edge)
                }
                levels[rank + 1].node(edge.second).update {
                    nonTreeEdges.add(edge)
                }
                ranks[edge] = rank + 1
            }
        }

        if (result == null) {
            val leftResult = node.left?.let { findReplacement(it, rank, additionalRoot) }
            if (leftResult != null)
                result = leftResult
        }
        if (result == null) {
            val rightResult = node.right?.let { findReplacement(it, rank, additionalRoot) }
            if (rightResult != null)
                result = rightResult
        }
        node.recalculate()
        return result
    }

    private inline fun lockComponents(a: Int, b: Int, body: () -> Unit) {
        var u = a
        var v = b

        while (true) {
            var uRoot = levels[0].root(u)
            var vRoot = levels[0].root(v)

            if (uRoot.priority > vRoot.priority) {
                val tmp = u
                u = v
                v = tmp
                val tmpNode = uRoot
                uRoot = vRoot
                vRoot = tmpNode
            }
            synchronized(uRoot) {
                synchronized(vRoot) {
                    if (uRoot == levels[0].root(u) && vRoot == levels[0].root(v)) {
                        body()
                        return
                    }
                }
            }
        }
    }
}