package connectivity.concurrent.tree

import connectivity.*
import connectivity.NO_EDGE
import connectivity.sequential.tree.*
import java.util.concurrent.*

class FineGrainedETTNode(@JvmField val priority: Int, isVertex: Boolean = true, treeEdge: Edge = NO_EDGE) {
    @Volatile
    @JvmField var parent: FineGrainedETTNode? = null
    @JvmField var left: FineGrainedETTNode? = null
    @JvmField var right: FineGrainedETTNode? = null
    @JvmField var size: Int = 1
    @JvmField val nonTreeEdges: SequentialEdgeSet? = if (isVertex) SequentialEdgeSet(INITIAL_SIZE) else null // for storing non-tree edges in general case
    @JvmField var hasNonTreeEdges: Boolean = false // for traversal
    @JvmField var currentLevelTreeEdge: Edge = treeEdge
    @JvmField var hasCurrentLevelTreeEdges: Boolean = currentLevelTreeEdge != NO_EDGE
}

class FineGrainedEulerTourTree(val size: Int) : TreeDynamicConnectivity {
    private val nodes: Array<FineGrainedETTNode>
    private val edgeToNode = ConcurrentEdgeMap<FineGrainedETTNode>()

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not edges
        nodes = Array(size) { FineGrainedETTNode(((1_000_000_007L * (it + 10)) % size).toInt()) }
    }

    override fun addEdge(u: Int, v: Int) = addEdge(u, v, true, null)

    fun addEdge(u: Int, v: Int, isCurrentLevelTreeEdge: Boolean, additionalRoot: FineGrainedETTNode?) {
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

        val random = ThreadLocalRandom.current()
        // create nodes corresponding to two directed copies of the new edge
        val uvNode = FineGrainedETTNode(
            size + random.nextInt(10 * size),
            false,
            if (isCurrentLevelTreeEdge && u < v) uvEdge else NO_EDGE
        )
        val vuNode = FineGrainedETTNode(
            size + random.nextInt(10 * size),
            false,
            if (isCurrentLevelTreeEdge && v < u) vuEdge else NO_EDGE
        )
        edgeToNode.put(uvEdge, uvNode)
        edgeToNode.put(vuEdge, vuNode)

        // merge (u,v), (v,u) edges and tours
        merge(merge(uRoot, uvNode), merge(vRoot, vuNode))
    }

    override fun removeEdge(u: Int, v: Int) {
        removeEdge(u, v, true)
    }

    fun removeEdge(u: Int, v: Int, doSplit: Boolean): Pair<FineGrainedETTNode, FineGrainedETTNode> {
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
        edgeToNode.removeIf(uvEdge)
        edgeToNode.removeIf(vuEdge)

        return Pair(component1, component2)
    }

    override fun connected(u: Int, v: Int): Boolean = root(u) === root(v)

    fun connected(u: Int, v: Int, additionalRoot: FineGrainedETTNode?): Boolean {
        return root(u, additionalRoot) === root(v, additionalRoot)
    }

    fun node(u: Int): FineGrainedETTNode = nodes[u]

    fun root(v: Int, additionalRoot: FineGrainedETTNode? = null): FineGrainedETTNode = root(nodes[v], additionalRoot)

    private fun root(n: FineGrainedETTNode, additionalRoot: FineGrainedETTNode? = null): FineGrainedETTNode {
        var node = n
        var parent = node.parent
        while (parent != null && node !== additionalRoot) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix, node, suffix] -> [node, suffix, prefix] (rotation)
    private fun makeFirst(node: FineGrainedETTNode, additionalRoot: FineGrainedETTNode?) {
        val root = root(node, additionalRoot)
        val position = node.position(additionalRoot)
        val div = split(root, position) // ([prefix], [node, suffix])
        merge(div.second, div.first)
    }

    private class SplitResults(var first: FineGrainedETTNode?, var second: FineGrainedETTNode?)

    /**
     * Splits the tree into two.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: FineGrainedETTNode?, sizeLeft: Int): SplitResults {
        if (node == null) return SplitResults(null, null)

        val toTheLeft = 1 + (node.left?.size ?: 0)
        return if (toTheLeft <= sizeLeft) {
            // node goes to the left part
            val division = split(node.right, sizeLeft - toTheLeft)
            node.right = division.first
            node.right?.parent = node
            node.recalculateAll()
            division.first = node
            division
        } else {
            // node goes to the right part
            val division = split(node.left, sizeLeft)
            node.left = division.second
            node.left?.parent = node
            node.recalculateAll()
            division.second = node
            division
        }
    }

    private fun merge(a: FineGrainedETTNode?, b: FineGrainedETTNode?): FineGrainedETTNode? {
        if (a == null) return b
        if (b == null) return a
        return if (a.priority < b.priority) {
            a.right = merge(a.right, b)
            a.right?.parent = a
            a.recalculateAll()
            a
        } else {
            b.left = merge(a, b.left)
            b.left?.parent = b
            b.recalculateAll()
            b
        }
    }

    // from 0 to n - 1
    private fun FineGrainedETTNode.position(additionalRoot: FineGrainedETTNode? = null): Int {
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

internal inline fun FineGrainedETTNode.recalculateAll() {
    recalculateSize()
    recalculateNonTreeEdges()
    recalculateTreeEdges()
}

internal inline fun FineGrainedETTNode.recalculateSize() {
    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
}

internal inline fun FineGrainedETTNode.recalculateTreeEdges() {
    hasCurrentLevelTreeEdges = currentLevelTreeEdge != connectivity.NO_EDGE || (left?.hasCurrentLevelTreeEdges ?: false) || (right?.hasCurrentLevelTreeEdges ?: false)
}

internal inline fun FineGrainedETTNode.recalculateNonTreeEdges() {
    hasNonTreeEdges = (nonTreeEdges?.isNotEmpty() ?: false) || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
}

internal fun FineGrainedETTNode.recalculateUpNonTreeEdges() {
    var node: FineGrainedETTNode? = this
    while (node != null) {
        val shouldHaveNonTreeEdges = (node.nonTreeEdges?.isNotEmpty() ?: false) || (node.left?.hasNonTreeEdges ?: false) || (node.right?.hasNonTreeEdges ?: false)
        if (node.hasNonTreeEdges == shouldHaveNonTreeEdges) return
        node.hasNonTreeEdges = shouldHaveNonTreeEdges
        node = node.parent
    }
}

internal inline fun FineGrainedETTNode.updateNonTreeEdges(body: FineGrainedETTNode.() -> Unit) {
    body()
    recalculateUpNonTreeEdges()
}