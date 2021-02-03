package connectivity.sequential.general

import connectivity.*
import connectivity.NO_EDGE
import connectivity.sequential.tree.*

interface DynamicConnectivity {
    fun addEdge(u: Int, v: Int)
    fun removeEdge(u: Int, v: Int)
    fun connected(u: Int, v: Int): Boolean
}

class SequentialDynamicConnectivity (private val size: Int) : DynamicConnectivity {
    private val levels: Array<SequentialEulerTourTree>
    private val ranks = SequentialEdgeMap<Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < size) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { SequentialEulerTourTree(size) }
    }

    override fun addEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        if (ranks[edge] != null) return
        ranks[edge] = 0
        if (!levels[0].connected(u, v)) {
            levels[0].addEdge(u, v)
        } else {
            levels[0].node(u).updateNonTreeEdges {
                nonTreeEdges!!.add(edge)
            }
            levels[0].node(v).updateNonTreeEdges {
                nonTreeEdges!!.add(edge)
            }
        }
    }

    override fun removeEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        val rank = ranks[edge] ?: return
        ranks.remove(edge)
        val level = levels[rank]

        val isNonTreeEdge = level.node(u).nonTreeEdges!!.contains(edge)
        if (isNonTreeEdge) {
            // just delete the non-tree edge
            level.node(u).updateNonTreeEdges {
                nonTreeEdges!!.remove(edge)
            }
            level.node(v).updateNonTreeEdges {
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

    override fun connected(u: Int, v: Int) = levels[0].connected(u, v)

    private fun increaseTreeEdgesRank(node: SequentialETTNode, u: Int, v: Int, rank: Int) {
        if (!node.hasCurrentLevelTreeEdges) return

        val treeEdge = node.currentLevelTreeEdge
        if (treeEdge != NO_EDGE) {
            node.currentLevelTreeEdge = NO_EDGE
            levels[rank + 1].addEdge(treeEdge.u(), treeEdge.v())
            ranks[treeEdge] = rank + 1
        }

        // recursive call for children
        node.left?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }
        node.right?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        // recalculate flags after updates
        node.recalculateTreeEdges()
    }

    private fun findReplacement(node: SequentialETTNode, rank: Int): Edge {
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
                    firstNode.updateNonTreeEdges {
                        nonTreeEdges!!.remove(edge)
                    }
                else
                    level.node(edge.v()).updateNonTreeEdges {
                        nonTreeEdges!!.remove(edge)
                    }
                iterator.remove()

                if (!level.connected(edge.u(), edge.v())) {
                    // is a replacement
                    result = edge
                    break
                } else {
                    // promote non-tree edge
                    levels[rank + 1].node(edge.u()).updateNonTreeEdges {
                        nonTreeEdges!!.add(edge)
                    }
                    levels[rank + 1].node(edge.v()).updateNonTreeEdges {
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
        node.recalculateNonTreeEdges()
        return result
    }
}