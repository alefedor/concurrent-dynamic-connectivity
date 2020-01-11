package connectivity.sequential.general

import connectivity.sequential.tree.*
import connectivity.sequential.tree.recalculateUp
import kotlin.math.max
import kotlin.math.min

class SequentialDynamicConnectivity (val nodes: Int) {
    private val levels: Array<SequentialEulerTourTree>
    private val ranks = HashMap<Pair<Int, Int>, Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < nodes) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { SequentialEulerTourTreeImpl(nodes) }
    }

    fun addEdge(u: Int, v: Int) {
        val edge = Pair(min(u, v), max(u, v))
        ranks[edge] = 0
        if (!levels[0].sameComponent(u, v)) {
            levels[0].addEdge(u, v)
        } else {
            val uNode = levels[0].node(u)
            val vNode = levels[0].node(v)
            uNode.nonTreeEdges.add(edge)
            uNode.recalculateUp()
            uNode.nonTreeEdges.add(edge)
            vNode.recalculateUp()
        }
    }

    fun removeEdge(u: Int, v: Int) {
        val edge = Pair(min(u, v), max(u, v))
        val rank = ranks[edge] ?: return
        ranks.remove(edge)
        val level = levels[rank]
        val isNonTreeEdge = level.node(u).nonTreeEdges.contains(edge)

        if (isNonTreeEdge) {
            val uNode = level.node(u)
            val vNode = level.node(v)
            uNode.nonTreeEdges.remove(edge)
            uNode.recalculateUp()
            vNode.nonTreeEdges.remove(edge)
            vNode.recalculateUp()
            return
        }

        for (r in 0..rank)
            levels[r].removeEdge(u, v)

        for (r in rank..0) {
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
                for (i in r..0)
                    levels[i].addEdge(replacementEdge.first, replacementEdge.second)
                break
            }
        }

    }

    fun sameComponents(u: Int, v: Int) = levels[0].sameComponent(u, v)

    private fun increaseTreeEdgesRank(node: Node, u: Int, v: Int, rank: Int) {
        if (!node.hasCurrentLevelTreeEdges) return

        node.currentLevelTreeEdge?.let {
            node.currentLevelTreeEdge = null
            levels[rank + 1].addEdge(it.first, it.second)
        }

        node.left?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        node.right?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        node.recalculate()
    }

    private fun findReplacement(node: Node, rank: Int): Pair<Int, Int>? {
        if (!node.hasNonTreeEdges) return null

        val iterator = node.nonTreeEdges.iterator()

        var result: Pair<Int, Int>? = null

        while (iterator.hasNext()) {
            val edge = iterator.next()
            iterator.remove()

            if (!levels[rank].sameComponent(edge.first, edge.second))// is replacement
                result = edge
        }

        if (result != null) {
            val leftResult = node.left?.let { findReplacement(it, rank) }
            if (leftResult != null)
                result = leftResult
        }
        if (result != null) {
            val rightResult = node.right?.let { findReplacement(it, rank) }
            if (rightResult != null)
                result = rightResult
        }
        node.recalculate()
        return result
    }
}