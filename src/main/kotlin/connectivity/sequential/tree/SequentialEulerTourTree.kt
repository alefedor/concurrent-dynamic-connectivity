package connectivity.sequential.tree

import kotlin.random.Random

interface EulerTourTree {
    fun addTreeEdge(u: Int, v: Int)
    fun removeTreeEdge(u: Int, v: Int)
    fun sameComponent(u: Int, v: Int): Boolean
    fun root(u: Int): Any // for grained locking

    open class Node(val priority: Int) {
        var left: Node? = null
        var right: Node? = null
        var size: Int = 1
    }
}

class SequentialEulerTourTree(val size: Int) : EulerTourTree {
    private val nodes: Array<SequentialNode>
    private val edgeToNode = mutableMapOf<Pair<Int, Int>, SequentialNode>()
    private val random = Random(0)

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not nodes
        val priorities = List(size) { it }.shuffled(random)
        nodes = Array(size) { SequentialNode(priorities[it]) }
    }

    override fun addTreeEdge(u: Int, v: Int) {
        val uNode = nodes[u]
        val vNode = nodes[v]

        // rotate tours so that u and v become first nodes of the tours
        makeFirst(uNode)
        makeFirst(vNode)

        val uRoot = root(uNode)
        val vRoot = root(vNode)

        val uv = SequentialNode(size + random.nextInt(10 * size))
        val vu = SequentialNode(size + random.nextInt(10 * size))
        edgeToNode[Pair(u, v)] = uv
        edgeToNode[Pair(v, u)] = vu
        // add uv and vu edges and merge tours
        merge(merge(uRoot, uv), merge(vRoot, vu))
    }

    override fun removeTreeEdge(u: Int, v: Int) {
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

        component1?.parent = null
        component2?.parent = null

        edgeToNode.remove(Pair(u, v))
        edgeToNode.remove(Pair(v, u))
    }

    override fun sameComponent(u: Int, v: Int): Boolean = root(u) === root(v)

    fun getEdges() = edgeToNode.keys

    override fun root(u: Int): EulerTourTree.Node = root(nodes[v])

    private fun root(n: Node): Node {
        var node = n
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix node suffix] -> [node suffix prefix] (rotation)
    private fun makeFirst(node: Node) {
        val root = root(node)
        val position = node.position()
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
            recalcSize(node)
            Pair(node, division.second)
        } else {
            // node goes to the right part
            val division = split(node.left, sizeLeft)
            node.left = division.second
            node.left?.parent = node
            recalcSize(node)
            Pair(division.first, node)
        }
    }

    private fun merge(a: Node?, b: Node?): Node? {
        if (a == null) return b
        if (b == null) return a
        return if (a.priority < b.priority) {
            a.right = merge(a.right, b)
            a.right?.parent = a
            recalcSize(a)
            a
        } else {
            b.left = merge(a, b.left)
            b.left?.parent = b
            recalcSize(b)
            b
        }
    }

    /// from 0 to n - 1
    private fun Node.position(): Int {
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

    private fun recalcSize(node: Node) {
        node.size = 1 + (node.left?.size ?: 0) + (node.right?.size ?: 0)
    }

    private class SequentialNode(priority: Int) : EulerTourTree.Node(priority) {
        var parent: SequentialNode? = null
    }
}