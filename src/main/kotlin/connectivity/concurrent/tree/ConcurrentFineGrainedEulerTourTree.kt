package connectivity.concurrent.tree

import connectivity.*
import connectivity.NO_EDGE
import connectivity.sequential.tree.*
import java.util.concurrent.*

class ConcurrentFineGrainedETTNode(@JvmField val priority: Int, isVertex: Boolean = true, treeEdge: Edge = NO_EDGE) {
    @Volatile
    @JvmField var parent: ConcurrentFineGrainedETTNode? = null
    @JvmField var left: ConcurrentFineGrainedETTNode? = null
    @JvmField var right: ConcurrentFineGrainedETTNode? = null
    @JvmField var size: Int = 1
    @JvmField val nonTreeEdges: SequentialEdgeSet? = if (isVertex) SequentialEdgeSet(INITIAL_SIZE) else null // for storing non-tree edges in general case
    @JvmField var hasNonTreeEdges: Boolean = false // for traversal
    @JvmField var currentLevelTreeEdge: Edge = treeEdge
    @JvmField var hasCurrentLevelTreeEdges: Boolean = currentLevelTreeEdge != NO_EDGE
    @Volatile
    @JvmField var version = 0
}

class ConcurrentFineGrainedEulerTourTree(val size: Int) : TreeDynamicConnectivity {
    private val nodes: Array<ConcurrentFineGrainedETTNode>
    private val edgeToNode = ConcurrentEdgeMap<ConcurrentFineGrainedETTNode>(size, false)

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not edges
        nodes = Array(size) { ConcurrentFineGrainedETTNode(((1_000_000_007L * (it + 10)) % size).toInt()) }
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
            uRoot.version.inc()
            vRoot.parent = uRoot
        } else {
            vRoot.version.inc()
            uRoot.parent = vRoot
        }

        val uvEdge = makeDirectedEdge(u, v)
        val vuEdge = makeDirectedEdge(v, u)

        val random = ThreadLocalRandom.current()
        // create nodes corresponding to two directed copies of the new edge
        val uvNode = ConcurrentFineGrainedETTNode(
            size + random.nextInt(10 * size),
            false,
            if (isCurrentLevelTreeEdge && u < v) uvEdge else NO_EDGE
        )
        val vuNode = ConcurrentFineGrainedETTNode(
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

    fun removeEdge(u: Int, v: Int, doSplit: Boolean): Pair<ConcurrentFineGrainedETTNode, ConcurrentFineGrainedETTNode> {
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
            component1.version.inc()
            component2.version.inc()
            component1.parent = null
            component2.parent = null
        }

        // remove two directed copies of the deleted edge
        edgeToNode.removeIf(uvEdge)
        edgeToNode.removeIf(vuEdge)

        return Pair(component1, component2)
    }

    override fun connected(u: Int, v: Int): Boolean {
        while (true) {
            val uRoot = root(u)
            val uRootVersion = uRoot.version
            val vRoot = root(v)
            val vRootVersion = vRoot.version
            if (!rereadRoot(u, uRoot, uRootVersion)) continue
            if (uRoot !== vRoot) {
                if (!rereadRoot(v, vRoot, vRootVersion)) continue
                if (!rereadRoot(u, uRoot, uRootVersion)) continue
            }
            return uRoot === vRoot
        }
    }

    // simple sequential connectivity check
    internal fun connectedSimple(u: Int, v: Int, additionalRoot: ConcurrentFineGrainedETTNode?): Boolean {
        return root(u, additionalRoot) === root(v, additionalRoot)
    }

    private inline fun rereadRoot(v: Int, wasRoot: ConcurrentFineGrainedETTNode, wasVersion: Int): Boolean {
        val root = root(v)
        return wasRoot === root && wasVersion == root.version
    }

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

    // [prefix, node, suffix] -> [node, suffix, prefix] (rotation)
    private fun makeFirst(node: ConcurrentFineGrainedETTNode, additionalRoot: ConcurrentFineGrainedETTNode?) {
        val root = root(node, additionalRoot)
        val position = node.position(additionalRoot)
        val div = split(root, position) // ([prefix], [node, suffix])
        merge(div.second, div.first)
    }

    private class SplitResults(var first: ConcurrentFineGrainedETTNode?, var second: ConcurrentFineGrainedETTNode?)

    /**
     * Splits the tree into two.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: ConcurrentFineGrainedETTNode?, sizeLeft: Int): SplitResults {
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

    private fun merge(a: ConcurrentFineGrainedETTNode?, b: ConcurrentFineGrainedETTNode?): ConcurrentFineGrainedETTNode? {
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
}

internal inline fun ConcurrentFineGrainedETTNode.recalculateAll() {
    recalculateSize()
    recalculateNonTreeEdges()
    recalculateTreeEdges()
}

internal inline fun ConcurrentFineGrainedETTNode.recalculateSize() {
    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
}

internal inline fun ConcurrentFineGrainedETTNode.recalculateTreeEdges() {
    hasCurrentLevelTreeEdges = currentLevelTreeEdge != connectivity.NO_EDGE || (left?.hasCurrentLevelTreeEdges ?: false) || (right?.hasCurrentLevelTreeEdges ?: false)
}

internal inline fun ConcurrentFineGrainedETTNode.recalculateNonTreeEdges() {
    hasNonTreeEdges = (nonTreeEdges?.isNotEmpty() ?: false) || (left?.hasNonTreeEdges ?: false) || (right?.hasNonTreeEdges ?: false)
}

internal fun ConcurrentFineGrainedETTNode.recalculateUpNonTreeEdges() {
    var node: ConcurrentFineGrainedETTNode? = this
    while (node != null) {
        val shouldHaveNonTreeEdges = (node.nonTreeEdges?.isNotEmpty() ?: false) || (node.left?.hasNonTreeEdges ?: false) || (node.right?.hasNonTreeEdges ?: false)
        if (node.hasNonTreeEdges == shouldHaveNonTreeEdges) return
        node.hasNonTreeEdges = shouldHaveNonTreeEdges
        node = node.parent
    }
}

internal inline fun ConcurrentFineGrainedETTNode.updateNonTreeEdges(body: ConcurrentFineGrainedETTNode.() -> Unit) {
    body()
    recalculateUpNonTreeEdges()
}