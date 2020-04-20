package connectivity.concurrent.tree

import connectivity.ConcurrentEdgeMap
import connectivity.SequentialEdgeMap
import connectivity.SequentialEdgeSet
import connectivity.sequential.tree.TreeDynamicConnectivity
import java.util.*
import kotlin.random.Random

class ConcurrentFineGrainedETTNode(val priority: Int, isVertex: Boolean = true, treeEdge: Pair<Int, Int>? = null) {
    @Volatile
    var parent: ConcurrentFineGrainedETTNode? = null
    var left: ConcurrentFineGrainedETTNode? = null
    var right: ConcurrentFineGrainedETTNode? = null
    var size: Int = 1
    val nonTreeEdges: MutableSet<Pair<Int, Int>> = if (isVertex) SequentialEdgeSet() else Collections.emptySet() // for storing non-tree edges in general case
    @Volatile
    var hasNonTreeEdges: Boolean = false // for traversal
    @Volatile
    var currentLevelTreeEdge: Pair<Int, Int>? = treeEdge
    @Volatile
    var hasCurrentLevelTreeEdges: Boolean = currentLevelTreeEdge != null
    @Volatile
    var version = 0
}

// Concurrent ETT version with concurrent map for fine-grained operations
class ConcurrentFineGrainedEulerTourTree(val size: Int) : TreeDynamicConnectivity {
    private val nodes: Array<ConcurrentFineGrainedETTNode>
    private val edgeToNode = ConcurrentEdgeMap<ConcurrentFineGrainedETTNode>()
    private val random = java.util.Random(0)

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not nodes
        val priorities = List(size) { it }.shuffled(random)
        nodes = Array(size) { ConcurrentFineGrainedETTNode(priorities[it]) }
    }

    override fun addEdge(u: Int, v: Int) = addEdge(u, v, true, null)

    fun addEdge(u: Int, v: Int, isCurrentLevelTreeEdge: Boolean, additionalRoot: ConcurrentFineGrainedETTNode?) {
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
            uRoot.version++
        } else {
            uRoot.parent = vRoot
            vRoot.version++
        }

        val uv = ConcurrentFineGrainedETTNode(
            size + random.nextInt(10 * size),
            false,
            if (isCurrentLevelTreeEdge) Pair(u, v) else null
        )
        val vu = ConcurrentFineGrainedETTNode(
            size + random.nextInt(10 * size),
            false,
            if (isCurrentLevelTreeEdge) Pair(v, u) else null
        )

        edgeToNode[Pair(u, v)] = uv
        edgeToNode[Pair(v, u)] = vu
        // add uv and vu edges and merge tours
        merge(merge(uRoot, uv), merge(vRoot, vu))
    }

    override fun removeEdge(u: Int, v: Int) {
        removeEdge(u, v, true)
    }

    fun removeEdge(u: Int, v: Int, doSplit: Boolean): Pair<ConcurrentFineGrainedETTNode, ConcurrentFineGrainedETTNode> {
        val edgeNode = edgeToNode[Pair(u, v)]!!
        val reverseEdgeNode = edgeToNode[Pair(v, u)]!!
        var leftPosition = edgeNode.position()
        var rightPosition = reverseEdgeNode.position()

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

        if (doSplit) {
            // linearization point
            // one of them was already null and the other lead to another tree
            component1!!.parent = null
            component2!!.parent = null
            component1.version.inc()
            component2.version.inc()
        }

        edgeToNode.remove(Pair(u, v))
        edgeToNode.remove(Pair(v, u))

        return Pair(component1!!, component2!!)
    }

    override fun connected(u: Int, v: Int): Boolean {
        if (u == v) return true

        while (true) {
            val uRoot = rootReader(u).withVersion()
            val vRoot = rootReader(v).withVersion()
            if (!rereadRoot(u, uRoot) ||
                !rereadRoot(v, vRoot)) continue
            return uRoot == vRoot
        }
    }

    internal fun connectedSimple(u: Int, v: Int, additionalRoot: ConcurrentFineGrainedETTNode?): Boolean {
        if (u == v) return true

        val uRoot = root(u, additionalRoot)
        val vRoot = root(v, additionalRoot)

        return uRoot === vRoot
    }

    fun state() = Pair(edgeToNode.keys, edgeToNode.values.map { it.priority }) // the tree is determined by (value, priority) pairs

    private fun rereadRoot(v: Int, was: Pair<ConcurrentFineGrainedETTNode, Int>): Boolean {
        return rootReader(v).withVersion() == was
    }

    private fun rootReader(v: Int): ConcurrentFineGrainedETTNode = rootReader(nodes[v])

    fun root(v : Int): ConcurrentFineGrainedETTNode = root(nodes[v])

    fun node(u: Int): ConcurrentFineGrainedETTNode = nodes[u]

    private fun root(v: Int, additionalRoot: ConcurrentFineGrainedETTNode? = null): ConcurrentFineGrainedETTNode = root(nodes[v], additionalRoot)

    private fun root(n: ConcurrentFineGrainedETTNode, additionalRoot: ConcurrentFineGrainedETTNode? = null): ConcurrentFineGrainedETTNode {
        var node = n
        var parent = node.parent
        while (parent != null && node !== additionalRoot) {
            node = parent
            parent = node.parent
        }
        return node
    }

    private fun rootReader(n: ConcurrentFineGrainedETTNode): ConcurrentFineGrainedETTNode {
        var node = n
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix node suffix] -> [node suffix prefix] (rotation)
    private fun makeFirst(node: ConcurrentFineGrainedETTNode, additionalRoot: ConcurrentFineGrainedETTNode?) {
        val root = root(node, additionalRoot)
        val position = node.position(additionalRoot)
        val div = split(root, position) // ([A], [node B])
        merge(div.second, div.first)
    }

    /**
     * Note, that the parent for the second tree will be same.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: ConcurrentFineGrainedETTNode?, sizeLeft: Int): Pair<ConcurrentFineGrainedETTNode?, ConcurrentFineGrainedETTNode?> {
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

    private fun merge(a: ConcurrentFineGrainedETTNode?, b: ConcurrentFineGrainedETTNode?): ConcurrentFineGrainedETTNode? {
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
    private fun ConcurrentFineGrainedETTNode.position(additionalRoot: ConcurrentFineGrainedETTNode? = null): Int {
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

    private fun ConcurrentFineGrainedETTNode.withVersion() = Pair(this, this.version)
}

internal fun ConcurrentFineGrainedETTNode.recalculate() {
    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
    hasNonTreeEdges = nonTreeEdges.isNotEmpty() || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
    hasCurrentLevelTreeEdges = currentLevelTreeEdge != null || (left?.hasCurrentLevelTreeEdges ?: false) || (right?.hasCurrentLevelTreeEdges ?: false)
}

internal fun ConcurrentFineGrainedETTNode.recalculateUp() {
    recalculate()
    parent?.recalculateUp()
}

internal fun ConcurrentFineGrainedETTNode.update(body: ConcurrentFineGrainedETTNode.() -> Unit) {
    body()
    recalculateUp()
}