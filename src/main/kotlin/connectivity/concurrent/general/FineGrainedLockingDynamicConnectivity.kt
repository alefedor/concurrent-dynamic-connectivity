package connectivity.concurrent.general

import connectivity.*
import connectivity.NO_EDGE
import connectivity.concurrent.tree.FineGrainedETTNode
import connectivity.concurrent.tree.FineGrainedEulerTourTree
import connectivity.concurrent.tree.recalculate
import connectivity.concurrent.tree.update
import connectivity.sequential.general.DynamicConnectivity

class FineGrainedLockingDynamicConnectivity(size: Int) : DynamicConnectivity {
    private val levels: Array<FineGrainedEulerTourTree>
    private val ranks = ConcurrentEdgeMap<Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < size) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { FineGrainedEulerTourTree(size) }
    }

    override fun addEdge(u: Int, v: Int) = lockComponents(u, v) {
        val edge = makeEdge(u, v)
        ranks[edge] = 0
        if (!levels[0].connected(u, v)) {
            levels[0].addEdge(u, v)
        } else {
            levels[0].node(u).update {
                nonTreeEdges!!.add(edge)
            }
            levels[0].node(v).update {
                nonTreeEdges!!.add(edge)
            }
        }
    }

    override fun removeEdge(u: Int, v: Int) = lockComponents(u, v) {
        val edge = makeEdge(u, v)
        val rank = ranks[edge]!!
        ranks.remove(edge)
        val level = levels[rank]

        val isNonTreeEdge = level.node(u).nonTreeEdges!!.contains(edge)
        if (isNonTreeEdge) {
            // just delete the non-tree edge
            level.node(u).update {
                nonTreeEdges!!.remove(edge)
            }
            level.node(v).update {
                nonTreeEdges!!.remove(edge)
            }
            return
        }

        for (r in 0..rank)
            levels[r].removeEdge(u, v)

        for (r in rank downTo 0) {
            val searchLevel = levels[r]
            var uRoot = searchLevel.root(u)
            var vRoot = searchLevel.root(v)

            // swap components if needed, so that the uRoot component is smaller
            if (uRoot.size > vRoot.size) {
                val tmp = uRoot
                uRoot = vRoot
                vRoot = tmp
            }

            // promote tree edges for the lesser component
            increaseTreeEdgesRank(uRoot, u, v, r)
            val replacementEdge = findReplacement(uRoot, r)
            if (replacementEdge != NO_EDGE) {
                // if a replacement is found, then add it to all levels <= r
                for (i in 0..r)
                    levels[i].addEdge(replacementEdge.u(), replacementEdge.v(), i == r)
                break
            }
        }
    }

    override fun connected(u: Int, v: Int): Boolean {
        var result = false
        lockComponents(u, v) {
            result = levels[0].connected(u, v)
        }
        return result
    }

    private fun increaseTreeEdgesRank(node: FineGrainedETTNode, u: Int, v: Int, rank: Int) {
        if (!node.hasCurrentLevelTreeEdges) return

        val treeEdge = node.currentLevelTreeEdge
        if (treeEdge != NO_EDGE) {
            node.currentLevelTreeEdge = NO_EDGE
            if (treeEdge.u() < treeEdge.v()) { // not to promote the same edge twice
                levels[rank + 1].addEdge(treeEdge.u(), treeEdge.v())
                ranks[treeEdge] = rank + 1
            }
        }

        // recursive call for children
        node.left?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }
        node.right?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        // recalculate flags after updates
        node.recalculate()
    }

    private fun findReplacement(node: FineGrainedETTNode, rank: Int): Edge {
        if (!node.hasNonTreeEdges) return NO_EDGE

        var result: Edge = NO_EDGE
        val level = levels[rank]

        node.nonTreeEdges?.let {
            val iterator = it.iterator()

            while (iterator.hasNext()) {
                val edge = iterator.nextLong()

                // remove edge from another node too
                val firstNode = level.node(edge.u())
                if (firstNode != node)
                    firstNode.update {
                        nonTreeEdges!!.remove(edge)
                    }
                else
                    level.node(edge.v()).update {
                        nonTreeEdges!!.remove(edge)
                    }
                iterator.remove()

                if (!level.connected(edge.u(), edge.v())) {
                    // is a replacement
                    result = edge
                    break
                } else {
                    // promote non-tree edge
                    levels[rank + 1].node(edge.u()).update {
                        nonTreeEdges!!.add(edge)
                    }
                    levels[rank + 1].node(edge.v()).update {
                        nonTreeEdges!!.add(edge)
                    }
                    ranks[edge] = rank + 1
                }
            }
        }

        if (result == NO_EDGE) {
            val leftResult = node.left?.let { findReplacement(it, rank) } ?: NO_EDGE
            if (leftResult != NO_EDGE)
                result = leftResult
        }
        if (result == NO_EDGE) {
            val rightResult = node.right?.let { findReplacement(it, rank) } ?: NO_EDGE
            if (rightResult != NO_EDGE)
                result = rightResult
        }
        node.recalculate()
        return result
    }

    fun root(u: Int): FineGrainedETTNode = levels[0].root(u)

    private inline fun lockComponents(a: Int, b: Int, body: () -> Unit) {
        var u = a
        var v = b

        while (true) {
            var uRoot = root(u)
            var vRoot = root(v)

            // lock the component with lesser priority first to avoid deadlock
            if (uRoot.priority > vRoot.priority) {
                val tmp = u
                u = v
                v = tmp
                val tmpNode = uRoot
                uRoot = vRoot
                vRoot = tmpNode
            }
            synchronized(uRoot) {
                synchronized(vRoot) {
                    if (uRoot == root(u) && vRoot == root(v)) {
                        body()
                        return
                    }
                }
            }
        }
    }
}