package connectivity.concurrent.general

import connectivity.*
import connectivity.NO_EDGE
import connectivity.concurrent.tree.*
import connectivity.concurrent.tree.updateNonTreeEdges
import connectivity.sequential.general.DynamicConnectivity
import connectivity.sequential.tree.updateNonTreeEdges

class NBReadsCoarseGrainedLockingDynamicConnectivity(private val size: Int) : DynamicConnectivity {
    private val levels: Array<ConcurrentEulerTourTree>
    private val ranks = SequentialEdgeMap<Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < size) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { ConcurrentEulerTourTree(size) }
    }

    @Synchronized
    override fun addEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        ranks[edge] = 0
        if (!levels[0].connectedSimple(u, v, null)) {
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

    @Synchronized
    override fun removeEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        val rank = ranks[edge]!!
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

        for (r in rank downTo 0) {
            // remove edge, but keep the parent link
            var (uRoot, vRoot) = levels[r].removeEdge(u, v, false)

            if (uRoot.size > vRoot.size) {
                val tmp = uRoot
                uRoot = vRoot
                vRoot = tmp
            }

            val lowerRoot = if (uRoot.parent != null) uRoot else vRoot

            // promote tree edges for less component
            increaseTreeEdgesRank(uRoot, u, v, r)
            val replacementEdge = findReplacement(uRoot, r, lowerRoot)
            if (replacementEdge != NO_EDGE) {
                for (i in r downTo 0) {
                    val lr = if (i == r) {
                        lowerRoot
                    } else {
                        val (ur, vr) = levels[i].removeEdge(u, v, false)
                        if (ur.parent != null) ur else vr
                    }

                    levels[i].addEdge(replacementEdge.u(), replacementEdge.v(), i == r, lr)
                }
                break
            } else {
                // linearization point, do an actual split on this level
                uRoot.version.inc()
                vRoot.version.inc()
                uRoot.parent = null
                vRoot.parent = null
            }
        }
    }

    override fun connected(u: Int, v: Int) = levels[0].connected(u, v)

    private fun increaseTreeEdgesRank(node: ConcurrentETTNode, u: Int, v: Int, rank: Int) {
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

    private fun findReplacement(node: ConcurrentETTNode, rank: Int, additionalRoot: ConcurrentETTNode): Edge {
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

                if (!level.connectedSimple(edge.u(), edge.v(), additionalRoot)) {
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
            val leftResult = node.left?.let { findReplacement(it, rank, additionalRoot) } ?: NO_EDGE
            if (leftResult != NO_EDGE)
                result = leftResult
        }
        if (result == NO_EDGE) {
            val rightResult = node.right?.let { findReplacement(it, rank, additionalRoot) } ?: NO_EDGE
            if (rightResult != NO_EDGE)
                result = rightResult
        }
        // recalculate flags after updates
        node.recalculateNonTreeEdges()
        return result
    }
}