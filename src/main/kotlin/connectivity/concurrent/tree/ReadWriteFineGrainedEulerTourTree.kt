package connectivity.concurrent.tree

import connectivity.*
import connectivity.NO_EDGE
import connectivity.concurrent.general.major.Node
import connectivity.sequential.tree.TreeDynamicConnectivity
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.HashSet
import kotlin.random.Random

class ReadWriteFineGrainedETTNode(val priority: Int, isVertex: Boolean = true, treeEdge: Edge = NO_EDGE) {
    @Volatile
    var parent: ReadWriteFineGrainedETTNode? = null
    var left: ReadWriteFineGrainedETTNode? = null
    var right: ReadWriteFineGrainedETTNode? = null
    var size: Int = 1
    val nonTreeEdges: SequentialEdgeSet? = if (isVertex) SequentialEdgeSet() else null // for storing non-tree edges in general case
    var hasNonTreeEdges: Boolean = false // for traversal
    var currentLevelTreeEdge: Edge = treeEdge
    var hasCurrentLevelTreeEdges: Boolean = currentLevelTreeEdge != NO_EDGE
    val lock: ReentrantReadWriteLock? = if (isVertex) ReentrantReadWriteLock() else null
}

class ReadWriteFineGrainedEulerTourTree(val size: Int) : TreeDynamicConnectivity {
    private val nodes: Array<ReadWriteFineGrainedETTNode>
    private val edgeToNode = ConcurrentEdgeMap<ReadWriteFineGrainedETTNode>()

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not edges
        val priorities = MutableList(size) { it }
        priorities.shuffle()
        nodes = Array(size) { ReadWriteFineGrainedETTNode(priorities[it]) }
    }

    override fun addEdge(u: Int, v: Int) = addEdge(u, v, true, null)

    fun addEdge(u: Int, v: Int, isCurrentLevelTreeEdge: Boolean, additionalRoot: ReadWriteFineGrainedETTNode?) {
        val uNode = nodes[u]
        val vNode = nodes[v]

        // rotate tours so that u and v become first nodes of the tours
        makeFirst(uNode, additionalRoot)
        makeFirst(vNode, additionalRoot)

        val uRoot = root(uNode, additionalRoot)
        val vRoot = root(vNode, additionalRoot)

        // linearization point
        if (uRoot.priority < vRoot.priority) {
            vRoot.parent = uRoot
        } else {
            uRoot.parent = vRoot
        }

        val uvEdge = makeDirectedEdge(u, v)
        val vuEdge = makeDirectedEdge(v, u)

        // create nodes corresponding to two directed copies of the new edge
        val uvNode = ReadWriteFineGrainedETTNode(
            size + Random.nextInt(10 * size),
            false,
            if (isCurrentLevelTreeEdge && u < v) uvEdge else NO_EDGE
        )
        val vuNode = ReadWriteFineGrainedETTNode(
            size + Random.nextInt(10 * size),
            false,
            if (isCurrentLevelTreeEdge && v < u) vuEdge else NO_EDGE
        )
        edgeToNode[uvEdge] = uvNode
        edgeToNode[vuEdge] = vuNode

        // merge (u,v), (v,u) edges and tours
        merge(merge(uRoot, uvNode), merge(vRoot, vuNode))
    }

    override fun removeEdge(u: Int, v: Int) {
        removeEdge(u, v, true)
    }

    fun removeEdge(u: Int, v: Int, doSplit: Boolean): Pair<ReadWriteFineGrainedETTNode, ReadWriteFineGrainedETTNode> {
        val uvEdge = makeDirectedEdge(u, v)
        val vuEdge = makeDirectedEdge(v, u)

        val edgeNode = edgeToNode[uvEdge]!!
        val reverseEdgeNode = edgeToNode[vuEdge]!!

        // get positions of the edge nodes in the tree
        var leftPosition = edgeNode.position()
        var rightPosition = reverseEdgeNode.position()
        if (leftPosition > rightPosition) {
            val tmp = rightPosition
            rightPosition = leftPosition
            leftPosition = tmp
        }

        val root = root(u)
        // cut the [leftPosition, rightPosition] segment out of the tree
        val div1 = split(root, rightPosition + 1)
        div1.first = split(div1.first, rightPosition).first // forget (v, u)
        val div2 = split(div1.first, leftPosition)
        val component1 = merge(div2.first, div1.second)!!
        val component2 = split(div2.second, 1).second!! // forget (u, v)

        if (doSplit) {
            // linearization point
            // one of them was already null and the other lead to another tree
            component1.parent = null
            component2.parent = null
        }

        // remove two directed copies of the deleted edge
        edgeToNode.remove(uvEdge)
        edgeToNode.remove(vuEdge)

        return Pair(component1, component2)
    }

    override fun connected(u: Int, v: Int): Boolean = root(u) === root(v)

    fun connected(u: Int, v: Int, additionalRoot: ReadWriteFineGrainedETTNode?): Boolean {
        return root(u, additionalRoot) === root(v, additionalRoot)
    }

    fun node(u: Int): ReadWriteFineGrainedETTNode = nodes[u]

    fun root(v: Int, additionalRoot: ReadWriteFineGrainedETTNode? = null): ReadWriteFineGrainedETTNode = root(nodes[v], additionalRoot)

    private fun root(n: ReadWriteFineGrainedETTNode, additionalRoot: ReadWriteFineGrainedETTNode? = null): ReadWriteFineGrainedETTNode {
        var node = n
        var parent = node.parent
        while (parent != null && node !== additionalRoot) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix, node, suffix] -> [node, suffix, prefix] (rotation)
    private fun makeFirst(node: ReadWriteFineGrainedETTNode, additionalRoot: ReadWriteFineGrainedETTNode?) {
        val root = root(node, additionalRoot)
        val position = node.position(additionalRoot)
        val div = split(root, position) // ([prefix], [node, suffix])
        merge(div.second, div.first)
    }

    private class SplitResults(var first: ReadWriteFineGrainedETTNode?, var second: ReadWriteFineGrainedETTNode?)

    /**
     * Splits the tree into two.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: ReadWriteFineGrainedETTNode?, sizeLeft: Int): SplitResults {
        if (node == null) return SplitResults(null, null)

        val toTheLeft = 1 + (node.left?.size ?: 0)
        return if (toTheLeft <= sizeLeft) {
            // node goes to the left part
            val division = split(node.right, sizeLeft - toTheLeft)
            node.right = division.first
            node.right?.parent = node
            node.recalculate()
            division.first = node
            division
        } else {
            // node goes to the right part
            val division = split(node.left, sizeLeft)
            node.left = division.second
            node.left?.parent = node
            node.recalculate()
            division.second = node
            division
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

    // from 0 to n - 1
    private fun ReadWriteFineGrainedETTNode.position(additionalRoot: ReadWriteFineGrainedETTNode? = null): Int {
        var position = (this.left?.size ?: 0)
        var current = this
        while (true) {
            val parent = current.parent ?: break
            if (current === additionalRoot) break
            if (current == parent.right)
                position += 1 + (parent.left?.size ?: 0)
            current = parent
        }
        return position
    }
}

internal inline fun ReadWriteFineGrainedETTNode.recalculate() {
    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
    hasNonTreeEdges = (nonTreeEdges?.isNotEmpty() ?: false) || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
    hasCurrentLevelTreeEdges = currentLevelTreeEdge != connectivity.NO_EDGE || (left?.hasCurrentLevelTreeEdges ?: false) || (right?.hasCurrentLevelTreeEdges ?: false)
}

internal fun ReadWriteFineGrainedETTNode.recalculateUp() {
    recalculate()
    parent?.recalculateUp()
}

internal fun ReadWriteFineGrainedETTNode.recalculateUpNonTreeEdges() {
    hasNonTreeEdges = true
    parent?.recalculateUpNonTreeEdges()
}

internal inline fun ReadWriteFineGrainedETTNode.updateNonTreeEdges(body: ReadWriteFineGrainedETTNode.() -> Unit) {
    body()
    recalculateUpNonTreeEdges()
}