package connectivity.concurrent.general.major

import connectivity.ConcurrentEdgeMap
import connectivity.concurrent.tree.ConcurrentFineGrainedETTNode
import connectivity.sequential.tree.TreeDynamicConnectivity
import org.cliffc.high_scale_lib.NonBlockingHashMap
import java.lang.Exception
import java.util.*
import kotlin.collections.HashSet
import kotlin.random.Random

class Node(val priority: Int, isVertex: Boolean = true, treeEdge: Pair<Int, Int>? = null) {
    @Volatile
    var parent: Node? = null
    var left: Node? = null
    var right: Node? = null
    var size: Int = 1
    val nonTreeEdges: MajorQueue<Pair<Int, Int>>? = if (isVertex) MajorQueue() else null // for storing non-tree edges in general case
    @Volatile
    var hasNonTreeEdges: Boolean = false // for traversal
    var currentLevelTreeEdge: Pair<Int, Int>? = treeEdge
    var hasCurrentLevelTreeEdges: Boolean = currentLevelTreeEdge != null
    @Volatile
    var version = 0
    @Volatile
    var removeEdgeOperation: RemovalOperationInfo? = null
}

class MajorConcurrentEulerTourTree(val size: Int) : TreeDynamicConnectivity {
    private val nodes: Array<Node>
    private val edgeToNode = ConcurrentEdgeMap<Node>()
    private val random = java.util.Random(0)

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not nodes
        val priorities = List(size) { it }.shuffled(random)
        nodes = Array(size) { Node(priorities[it]) }
    }

    override fun addEdge(u: Int, v: Int) = addEdge(u, v, true, null)

    fun addEdge(u: Int, v: Int, isCurrentLevelTreeEdge: Boolean, additionalRoot: Node?) {
        val uNode = nodes[u]
        val vNode = nodes[v]

        // rotate tours so that u and v become first nodes of the tours
        makeFirst(uNode, additionalRoot)
        makeFirst(vNode, additionalRoot)

        val uRoot = root(uNode, additionalRoot)
        val vRoot = root(vNode, additionalRoot)

        // linearization point
        if (uRoot == vRoot) {
            println("!")
        }

        if (uRoot.priority < vRoot.priority) {
            vRoot.parent = uRoot
            uRoot.version++
        } else {
            uRoot.parent = vRoot
            vRoot.version++
        }

        val uv = Node(size + random.nextInt(10 * size), false, if (isCurrentLevelTreeEdge) Pair(u, v) else null)
        val vu = Node(size + random.nextInt(10 * size), false, if (isCurrentLevelTreeEdge) Pair(v, u) else null)

        edgeToNode[Pair(u, v)] = uv
        edgeToNode[Pair(v, u)] = vu
        // add uv and vu edges and merge tours
        merge(merge(uRoot, uv), merge(vRoot, vu))
    }

    override fun removeEdge(u: Int, v: Int) {
        removeEdge(u, v, true)
    }

    fun removeEdge(u: Int, v: Int, doSplit: Boolean): Pair<Node, Node> {
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
            val uRoot = root(u)
            val uRootVersion = uRoot.version
            val vRoot = root(v)
            val vRootVersion = vRoot.version
            if (!rereadRoot(u, uRoot, uRootVersion)) continue
            if (vRoot != uRoot) return false
            if (!rereadRoot(v, vRoot, vRootVersion)) continue
            return true
        }
    }

    internal fun connectedSimple(u: Int, v: Int, additionalRoot: Node? = null): Boolean {
        if (u == v) return true

        val uRoot = root(u, additionalRoot)
        val vRoot = root(v, additionalRoot)

        return uRoot === vRoot
    }

    fun state() = Pair(edgeToNode.keys, edgeToNode.values.map { it.priority }) // the tree is determined by (value, priority) pairs

    private inline fun rereadRoot(v: Int, wasRoot: Node, wasVersion: Int): Boolean {
        val root = root(v)
        return wasRoot === root && wasVersion == root.version
    }

    fun root(v : Int): Node = root(nodes[v])

    fun node(u: Int): Node = nodes[u]

    private fun root(v: Int, additionalRoot: Node? = null): Node = root(nodes[v], additionalRoot)

    private fun root(n: Node, additionalRoot: Node? = null): Node {
        var node = n
        var parent = node.parent
        while (parent != null && node !== additionalRoot) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix node suffix] -> [node suffix prefix] (rotation)
    private fun makeFirst(node: Node, additionalRoot: Node?) {
        val root = root(node, additionalRoot)
        val position = node.position(additionalRoot)
        val div = split(root, position) // ([A], [node B])
        merge(div.second, div.first)
    }

    /**
     * Note, that the parent for the second tree will be same.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: Node?, sizeLeft: Int): Pair<Node?, Node?> {
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

    private fun merge(a: Node?, b: Node?): Node? {
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
    private fun Node.position(additionalRoot: Node? = null): Int {
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

    private fun Node.withVersion() = Pair(this, this.version)
}

internal fun Node.recalculate() {
    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
    val shouldHaveNonTreeEdges = (nonTreeEdges?.isNotEmpty() ?: false) || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
    hasNonTreeEdges = shouldHaveNonTreeEdges
    if (!shouldHaveNonTreeEdges) {
        // the second check is needed, because could accidentally rewrite value written by non-blocking additions
        val shouldHaveNonTreeEdges2 = (nonTreeEdges?.isNotEmpty() ?: false) || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
        if (shouldHaveNonTreeEdges2)
            hasNonTreeEdges = true
    }
    hasCurrentLevelTreeEdges = currentLevelTreeEdge != null || (left?.hasCurrentLevelTreeEdges ?: false) || (right?.hasCurrentLevelTreeEdges ?: false)
}

internal fun Node.recalculateUp() {
    recalculate()
    parent?.recalculateUp()
}

internal fun Node.update(body: Node.() -> Unit) {
    body()
    recalculateUp()
}

internal fun Node.recalculateUpNonTreeEdges() {
    hasNonTreeEdges = true
    parent?.recalculateUpNonTreeEdges()
}

internal fun Node.updateNonTreeEdges(body: Node.() -> Unit) {
    body()
    recalculateUpNonTreeEdges()
}