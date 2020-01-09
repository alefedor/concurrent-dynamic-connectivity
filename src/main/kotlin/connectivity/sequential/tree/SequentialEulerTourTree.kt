package connectivity.sequential.tree

import kotlin.random.Random

interface SequentialEulerTourTree {
    fun addTreeEdge(u: Int, v: Int)
    fun removeTreeEdge(u: Int, v: Int)
    fun sameComponent(u: Int, v: Int): Boolean
    fun root(u: Int): Node // for grained locking and traversals

    class Node(val priority: Int) {
        var parent: Node? = null
        var left: Node? = null
        var right: Node? = null
        var size: Int = 1
    }
}

class SequentialEulerTourTreeImpl(val size: Int) : SequentialEulerTourTree {
    private val nodes: Array<SequentialEulerTourTree.Node>
    private val edgeToNode = mutableMapOf<Pair<Int, Int>, SequentialEulerTourTree.Node>()
    private val random = Random(0)

    init {
        // priorities for vertices are numbers in [0, size)
        // priorities for edges are random numbers in [size, 11 * size)
        // priorities for nodes are less so that roots will be always vertices, not nodes
        val priorities = List(size) { it }.shuffled(random)
        nodes = Array(size) { SequentialEulerTourTree.Node(priorities[it]) }
    }

    override fun addTreeEdge(u: Int, v: Int) {
        val uNode = nodes[u]
        val vNode = nodes[v]

        // rotate tours so that u and v become first nodes of the tours
        makeFirst(uNode)
        makeFirst(vNode)

        val uRoot = root(uNode)
        val vRoot = root(vNode)
        val uv = SequentialEulerTourTree.Node(size + random.nextInt(10 * size))
        val vu = SequentialEulerTourTree.Node(size + random.nextInt(10 * size))

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

    override fun sameComponent(u: Int, v: Int): Boolean = root(u) == root(v)

    override fun root(u: Int): SequentialEulerTourTree.Node = root(nodes[u])

    private fun root(n: SequentialEulerTourTree.Node): SequentialEulerTourTree.Node {
        var node = n
        var parent = node.parent
        while (parent != null) {
            node = parent
            parent = node.parent
        }
        return node
    }

    // [prefix node suffix] -> [node suffix prefix] (rotation)
    private fun makeFirst(node: SequentialEulerTourTree.Node) {
        val root = root(node)
        val position = node.position()
        val div = split(root, position) // ([A], [node B])
        merge(div.second, div.first)
    }

    /**
     * Note, that the parent for the second tree will be same.
     * [sizeLeft] is the number of nodes that should go to the left tree
     */
    private fun split(node: SequentialEulerTourTree.Node?, sizeLeft: Int): Pair<SequentialEulerTourTree.Node?, SequentialEulerTourTree.Node?> {
        if (node == null) return Pair(null, null)

        val toTheLeft = 1 + (node.left?.size ?: 0)
        return if (toTheLeft <= sizeLeft) {
            // node goes to the left part
            val division = split(node.right, sizeLeft - toTheLeft)
            node.right = division.first
            node.right?.parent = node
            recalculateSize(node)
            Pair(node, division.second)
        } else {
            // node goes to the right part
            val division = split(node.left, sizeLeft)
            node.left = division.second
            node.left?.parent = node
            recalculateSize(node)
            Pair(division.first, node)
        }
    }

    private fun merge(a: SequentialEulerTourTree.Node?, b: SequentialEulerTourTree.Node?): SequentialEulerTourTree.Node? {
        if (a == null) return b
        if (b == null) return a
        return if (a.priority < b.priority) {
            a.right = merge(a.right, b)
            a.right?.parent = a
            recalculateSize(a)
            a
        } else {
            b.left = merge(a, b.left)
            b.left?.parent = b
            recalculateSize(b)
            b
        }
    }

    /// from 0 to n - 1
    private fun SequentialEulerTourTree.Node.position(): Int {
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

    private fun recalculateSize(node: SequentialEulerTourTree.Node) {
        node.size = 1 + (node.left?.size ?: 0) + (node.right?.size ?: 0)
    }
}