package connectivity.concurrent.general

import connectivity.ConcurrentEdgeMap
import connectivity.concurrent.tree.ReadWriteFineGrainedETTNode
import connectivity.concurrent.tree.ReadWriteFineGrainedEulerTourTree
import connectivity.concurrent.tree.recalculate
import connectivity.concurrent.tree.update
import connectivity.sequential.general.DynamicConnectivity
import kotlin.collections.HashMap
import kotlin.math.max
import kotlin.math.min

class FineGrainedReadWriteLockingDynamicConnectivity(size: Int) : DynamicConnectivity {
    private val levels: Array<ReadWriteFineGrainedEulerTourTree>
    private val ranks = ConcurrentEdgeMap<Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < size) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { ReadWriteFineGrainedEulerTourTree(size) }
    }

    override fun addEdge(u: Int, v: Int) = lockComponentsWrite(u, v) {
        val edge = Pair(min(u, v), max(u, v))
        ranks[edge] = 0
        if (!levels[0].connected(u, v)) {
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

    override fun removeEdge(u: Int, v: Int) = lockComponentsWrite(u, v) {
        val edge = Pair(min(u, v), max(u, v))
        val rank = ranks[edge] ?: return
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

        for (r in 0..rank)
            levels[r].removeEdge(u, v)

        for (r in rank downTo 0) {
            val searchLevel = levels[r]
            var uRoot = searchLevel.root(u)
            var vRoot = searchLevel.root(v)

            if (uRoot.size > vRoot.size) {
                val tmp = uRoot
                uRoot = vRoot
                vRoot = tmp
            }

            // promote tree edges for less component

            increaseTreeEdgesRank(uRoot, u, v, r)
            val replacementEdge = findReplacement(uRoot, r)
            if (replacementEdge != null) {
                for (i in 0..r)
                    levels[i].addEdge(replacementEdge.first, replacementEdge.second, i == r)
                break
            }
        }

    }

    override fun connected(u: Int, v: Int): Boolean {
        var result = false
        lockComponentsRead(u, v) {
            result = levels[0].connected(u, v)
        }
        return result
    }

    fun root(u: Int): ReadWriteFineGrainedETTNode = levels[0].root(u)

    private fun increaseTreeEdgesRank(node: ReadWriteFineGrainedETTNode, u: Int, v: Int, rank: Int) {
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

    private fun findReplacement(node: ReadWriteFineGrainedETTNode, rank: Int): Pair<Int, Int>? {
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

            if (!levels[rank].connected(edge.first, edge.second)) {
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
            val leftResult = node.left?.let { findReplacement(it, rank) }
            if (leftResult != null)
                result = leftResult
        }
        if (result == null) {
            val rightResult = node.right?.let { findReplacement(it, rank) }
            if (rightResult != null)
                result = rightResult
        }
        node.recalculate()
        return result
    }

    private inline fun lockComponentsRead(u: Int, v: Int, body: () -> Unit) {
        while (true) {
            val uRoot = root(u)
            val vRoot = root(v)

            if (uRoot.priority < vRoot.priority) {
                uRoot.lock!!.readLock().lock()
                vRoot.lock!!.readLock().lock()
            } else {
                vRoot.lock!!.readLock().lock()
                uRoot.lock!!.readLock().lock()
            }

            if (uRoot == root(u) && vRoot == root(v)) {
                body()
                uRoot.lock.readLock().unlock()
                vRoot.lock.readLock().unlock()
                break
            }

            uRoot.lock.readLock().unlock()
            vRoot.lock.readLock().unlock()
        }
    }

    private inline fun lockComponentsWrite(u: Int, v: Int, body: () -> Unit) {
        while (true) {
            val uRoot = root(u)
            val vRoot = root(v)

            if (uRoot.priority < vRoot.priority) {
                uRoot.lock!!.writeLock().lock()
                vRoot.lock!!.writeLock().lock()
            } else {
                vRoot.lock!!.writeLock().lock()
                uRoot.lock!!.writeLock().lock()
            }

            if (uRoot == root(u) && vRoot == root(v)) {
                body()
                uRoot.lock.writeLock().unlock()
                vRoot.lock.writeLock().unlock()
                break
            }

            uRoot.lock.writeLock().unlock()
            vRoot.lock.writeLock().unlock()
        }
    }
}