package connectivity.concurrent.general

import connectivity.sequential.general.DynamicConnectivity
import connectivity.sequential.tree.TreeDynamicConnectivity
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class FineGrainedReadWriteLockingDynamicConnectivity(size: Int) : DynamicConnectivity {
    private val connectivity = FineGrainedReadWriteDynamicConnectivity(size)

    @Synchronized
    override fun addEdge(u: Int, v: Int) = lockComponentsWrite(u, v) { connectivity.addEdge(u, v) }

    @Synchronized
    override fun removeEdge(u: Int, v: Int) = lockComponentsWrite(u, v) { connectivity.removeEdge(u, v) }

    @Synchronized
    override fun connected(u: Int, v: Int): Boolean {
        var result: Boolean = false
        lockComponentsRead(u, v) { result = connectivity.connected(u, v) }
        return result
    }

    private inline fun lockComponentsRead(u: Int, v: Int, body: () -> Unit) {
        lockComponentRead(min(u, v)) { // min/max not to get into deadlock
            lockComponentRead(max(u, v)) {
                body()
            }
        }
    }

    private inline fun lockComponentRead(u: Int, body: () -> Unit) {
        while (true) {
            val root = connectivity.root(u)
            root.lock!!.readLock().lock()

            if (root == connectivity.root(u)) {
                body()
                root.lock.readLock().unlock()
                break
            }

            body()

            root.lock.readLock().unlock()
        }
    }

    private inline fun lockComponentsWrite(u: Int, v: Int, body: () -> Unit) {
        lockComponentWrite(min(u, v)) { // min/max not to get into deadlock
            lockComponentWrite(max(u, v)) {
                body()
            }
        }
    }

    private inline fun lockComponentWrite(u: Int, body: () -> Unit) {
        while (true) {
            val root = connectivity.root(u)
            root.lock!!.writeLock().lock()

            if (root == connectivity.root(u)) {
                body()
                root.lock.writeLock().unlock()
                break
            }

            body()

            root.lock.writeLock().unlock()
        }
    }
}

class FineGrainedReadWriteDynamicConnectivity (private val size: Int) : DynamicConnectivity {
    private val levels: Array<FineGrainedReadWriteEulerTourTree>
    private val ranks = HashMap<Pair<Int, Int>, Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < size) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { FineGrainedReadWriteEulerTourTree(size) }
    }

    override fun addEdge(u: Int, v: Int) {
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

    override fun removeEdge(u: Int, v: Int) {
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

    override fun connected(u: Int, v: Int) = levels[0].connected(u, v)

    fun root(u: Int): FGRWNode = levels[0].root(u)

    private fun increaseTreeEdgesRank(node: FGRWNode, u: Int, v: Int, rank: Int) {
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

    private fun findReplacement(node: FGRWNode, rank: Int): Pair<Int, Int>? {
        if (!node.hasNonTreeEdges) return null

        val iterator = node.nonTreeEdges.iterator()

        var result: Pair<Int, Int>? = null

        while (iterator.hasNext()) {
            val edge = iterator.next()
            val firstFGRWNode = levels[rank].node(edge.first)
            if (firstFGRWNode != node)
                firstFGRWNode.update {
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
}

class FGRWNode(val priority: Int, isVertex: Boolean = true, treeEdge: Pair<Int, Int>? = null) {
    @Volatile
    var parent: FGRWNode? = null
    var left: FGRWNode? = null
    var right: FGRWNode? = null
    var size: Int = 1
    val nonTreeEdges: MutableSet<Pair<Int, Int>> = if (isVertex) HashSet() else Collections.emptySet() // for storing non-tree edges in general case
    var hasNonTreeEdges: Boolean = false // for traversal
    var currentLevelTreeEdge: Pair<Int, Int>? = treeEdge
    var hasCurrentLevelTreeEdges: Boolean = currentLevelTreeEdge != null
    val lock: ReentrantReadWriteLock? = if (isVertex) ReentrantReadWriteLock() else null
}

class FineGrainedReadWriteEulerTourTree(val size: Int) : TreeDynamicConnectivity {
    private val nodes: Array<FGRWNode>
    private val edgeToFGRWNode = mutableMapOf<Pair<Int, Int>, FGRWNode>()
    private val random = Random(0)

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not nodes
        val priorities = List(size) { it }.shuffled(random)
        nodes = Array(size) { FGRWNode(priorities[it]) }
    }

    override fun addEdge(u: Int, v: Int) = addEdge(u, v, true)

    fun addEdge(u: Int, v: Int, isCurrentLevelTreeEdge: Boolean) {
        val uFGRWNode = nodes[u]
        val vFGRWNode = nodes[v]

        // rotate tours so that u and v become first nodes of the tours
        makeFirst(uFGRWNode)
        makeFirst(vFGRWNode)

        val uRoot = root(uFGRWNode)
        val vRoot = root(vFGRWNode)

        val uv = FGRWNode(size + random.nextInt(10 * size), false, if (isCurrentLevelTreeEdge) Pair(u, v) else null)
        val vu = FGRWNode(size + random.nextInt(10 * size), false, if (isCurrentLevelTreeEdge) Pair(v, u) else null)

        edgeToFGRWNode[Pair(u, v)] = uv
        edgeToFGRWNode[Pair(v, u)] = vu
        // add uv and vu edges and merge tours
        merge(merge(uRoot, uv), merge(vRoot, vu))
    }

    override fun removeEdge(u: Int, v: Int) {
        val edgeFGRWNode = edgeToFGRWNode[Pair(u, v)]!!
        val reverseEdgeFGRWNode = edgeToFGRWNode[Pair(v, u)]!!

        var leftPosition = edgeFGRWNode.position()
        var rightPosition = reverseEdgeFGRWNode.position()

        if (leftPosition > rightPosition) {
            val tmp = rightPosition
            rightPosition = leftPosition
            leftPosition = tmp
        }

        val root = root(u)
        // cut the [leftPosition, rightPosition] segment out of the tree
        var div1 = split(root, rightPosition + 1)
        div1 = Pair(split(div1.first, rightPosition).first, div1.second) // forget (v, u)
        val div2 = split(div1.first, leftPosition)

        val component1 = merge(div2.first, div1.second)
        val component2 = split(div2.second, 1).second // forget (u, v)

        component1?.parent = null
        component2?.parent = null

        edgeToFGRWNode.remove(Pair(u, v))
        edgeToFGRWNode.remove(Pair(v, u))
    }

    override fun connected(u: Int, v: Int): Boolean = root(u) == root(v)

    fun root(u: Int): FGRWNode = root(nodes[u])

    fun node(u: Int): FGRWNode = nodes[u]

    private fun root(n: FGRWNode): FGRWNode {
        var node = n
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix node suffix] -> [node suffix prefix] (rotation)
    private fun makeFirst(node: FGRWNode) {
        val root = root(node)
        val position = node.position()
        val div = split(root, position) // ([A], [node B])
        merge(div.second, div.first)
    }

    /**
     * Note, that the parent for the second tree will be same.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: FGRWNode?, sizeLeft: Int): Pair<FGRWNode?, FGRWNode?> {
        if (node == null) return Pair(null, null)

        val toTheLeft = 1 + (node.left?.size ?: 0)
        return if (toTheLeft <= sizeLeft) {
            // node goes to the left part
            val division = split(node.right, sizeLeft - toTheLeft)
            node.right = division.first
            node.right?.parent = node
            node.recalculate()
            Pair(node, division.second)
        } else {
            // node goes to the right part
            val division = split(node.left, sizeLeft)
            node.left = division.second
            node.left?.parent = node
            node.recalculate()
            Pair(division.first, node)
        }
    }

    private fun merge(a: FGRWNode?, b: FGRWNode?): FGRWNode? {
        if (a == null) return b
        if (b == null) return a
        return if (a.priority < b.priority) {
            a.right = merge(a.right, b)
            a.right?.parent = a
            a.recalculate()
            a
        } else {
            b.left = merge(a, b.left)
            b.left?.parent = b
            b.recalculate()
            b
        }
    }

    /// from 0 to n - 1
    private fun FGRWNode.position(): Int {
        var position = (this.left?.size ?: 0)
        var current = this
        while (true) {
            val parent = current.parent ?: break
            if (current == parent.right)
                position += 1 + (parent.left?.size ?: 0)
            current = parent
        }
        return position
    }
}

internal fun FGRWNode.recalculate() {
    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
    hasNonTreeEdges = nonTreeEdges.isNotEmpty() || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
    hasCurrentLevelTreeEdges = currentLevelTreeEdge != null || (left?.hasCurrentLevelTreeEdges ?: false) || (right?.hasCurrentLevelTreeEdges ?: false)
}

internal fun FGRWNode.recalculateUp() {
    recalculate()
    parent?.recalculateUp()
}

internal fun FGRWNode.update(body: FGRWNode.() -> Unit) {
    body()
    recalculateUp()
}