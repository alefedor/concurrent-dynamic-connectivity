package connectivity.concurrent.tree

import connectivity.SequentialEdgeSet
import connectivity.sequential.tree.TreeDynamicConnectivity
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.HashSet
import kotlin.random.Random

class ReadWriteFineGrainedETTNode(val priority: Int, isVertex: Boolean = true, treeEdge: Pair<Int, Int>? = null) {
    @Volatile
    var parent: ReadWriteFineGrainedETTNode? = null
    var left: ReadWriteFineGrainedETTNode? = null
    var right: ReadWriteFineGrainedETTNode? = null
    var size: Int = 1
    val nonTreeEdges: MutableSet<Pair<Int, Int>> = if (isVertex) SequentialEdgeSet() else Collections.emptySet() // for storing non-tree edges in general case
    var hasNonTreeEdges: Boolean = false // for traversal
    var currentLevelTreeEdge: Pair<Int, Int>? = treeEdge
    var hasCurrentLevelTreeEdges: Boolean = currentLevelTreeEdge != null
    val lock: ReentrantReadWriteLock? = if (isVertex) ReentrantReadWriteLock() else null
}

// Sequential versions with stored RWLocks. Is not a correct concurrent ETT itself
class ReadWriteFineGrainedEulerTourTree(val size: Int) : TreeDynamicConnectivity {
    private val nodes: Array<ReadWriteFineGrainedETTNode>
    private val edgeToNode = mutableMapOf<Pair<Int, Int>, ReadWriteFineGrainedETTNode>()
    private val random = java.util.Random(0)

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not nodes
        val priorities = List(size) { it }.shuffled(random)
        nodes = Array(size) { ReadWriteFineGrainedETTNode(priorities[it]) }
    }

    override fun addEdge(u: Int, v: Int) = addEdge(u, v, true)

    fun addEdge(u: Int, v: Int, isCurrentLevelTreeEdge: Boolean) {
        val uReadWriteFineGrainedETTNode = nodes[u]
        val vReadWriteFineGrainedETTNode = nodes[v]

        // rotate tours so that u and v become first nodes of the tours
        makeFirst(uReadWriteFineGrainedETTNode)
        makeFirst(vReadWriteFineGrainedETTNode)

        val uRoot = root(uReadWriteFineGrainedETTNode)
        val vRoot = root(vReadWriteFineGrainedETTNode)

        val uv = ReadWriteFineGrainedETTNode(size + random.nextInt(10 * size), false, if (isCurrentLevelTreeEdge) Pair(u, v) else null)
        val vu = ReadWriteFineGrainedETTNode(size + random.nextInt(10 * size), false, if (isCurrentLevelTreeEdge) Pair(v, u) else null)

        edgeToNode[Pair(u, v)] = uv
        edgeToNode[Pair(v, u)] = vu
        // add uv and vu edges and merge tours
        merge(merge(uRoot, uv), merge(vRoot, vu))
    }

    override fun removeEdge(u: Int, v: Int) {
        val edgeReadWriteFineGrainedETTNode = edgeToNode[Pair(u, v)]!!
        val reverseEdgeReadWriteFineGrainedETTNode = edgeToNode[Pair(v, u)]!!

        var leftPosition = edgeReadWriteFineGrainedETTNode.position()
        var rightPosition = reverseEdgeReadWriteFineGrainedETTNode.position()

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

        edgeToNode.remove(Pair(u, v))
        edgeToNode.remove(Pair(v, u))
    }

    override fun connected(u: Int, v: Int): Boolean = root(u) == root(v)

    fun root(u: Int): ReadWriteFineGrainedETTNode = root(nodes[u])

    fun node(u: Int): ReadWriteFineGrainedETTNode = nodes[u]

    private fun root(n: ReadWriteFineGrainedETTNode): ReadWriteFineGrainedETTNode {
        var node = n
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix node suffix] -> [node suffix prefix] (rotation)
    private fun makeFirst(node: ReadWriteFineGrainedETTNode) {
        val root = root(node)
        val position = node.position()
        val div = split(root, position) // ([A], [node B])
        merge(div.second, div.first)
    }

    /**
     * Note, that the parent for the second tree will be same.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: ReadWriteFineGrainedETTNode?, sizeLeft: Int): Pair<ReadWriteFineGrainedETTNode?, ReadWriteFineGrainedETTNode?> {
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

    private fun merge(a: ReadWriteFineGrainedETTNode?, b: ReadWriteFineGrainedETTNode?): ReadWriteFineGrainedETTNode? {
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
    private fun ReadWriteFineGrainedETTNode.position(): Int {
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

internal fun ReadWriteFineGrainedETTNode.recalculate() {
    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
    hasNonTreeEdges = nonTreeEdges.isNotEmpty() || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
    hasCurrentLevelTreeEdges = currentLevelTreeEdge != null || (left?.hasCurrentLevelTreeEdges ?: false) || (right?.hasCurrentLevelTreeEdges ?: false)
}

internal fun ReadWriteFineGrainedETTNode.recalculateUp() {
    recalculate()
    parent?.recalculateUp()
}

internal fun ReadWriteFineGrainedETTNode.update(body: ReadWriteFineGrainedETTNode.() -> Unit) {
    body()
    recalculateUp()
}