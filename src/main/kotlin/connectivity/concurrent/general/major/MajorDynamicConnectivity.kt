package connectivity.concurrent.general.major

import connectivity.*
import connectivity.sequential.general.DynamicConnectivity
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

class MajorDynamicConnectivity(private val size: Int) : DynamicConnectivity {
    private val levels: Array<MajorConcurrentEulerTourTree>
    private val statuses = ConcurrentEdgeMap<AtomicReference<EdgeStatus>>()
    private val ranks = ConcurrentEdgeMap<Int>()

    init {
        var levelNumber = 1
        var maxSize = 1
        while (maxSize < size) {
            levelNumber++
            maxSize *= 2
        }
        levels = Array(levelNumber) { MajorConcurrentEulerTourTree(size) }
    }

    override fun addEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        ranks[edge] = 0
        val status = AtomicReference(EdgeStatus.INITIAL)
        statuses[edge] = status
        while (true) {
            val currentStatus = status.get()
            if (currentStatus != EdgeStatus.INITIAL && currentStatus != EdgeStatus.SPECIAL)
                return // someone already finished the edge addition
            if (!connected(u, v)) {
                // change status to SPECIAL before locking
                if (currentStatus != EdgeStatus.SPECIAL) {
                    if (!status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.SPECIAL)) {
                        if (status.get() != EdgeStatus.SPECIAL)
                            return // someone already finished the edge addition
                    }
                }
                lockComponents(u, v) {
                    doAddEdge(u, v)
                    return
                }
            } else {
                // change status to INITIAL before non-blocking addition
                if (currentStatus == EdgeStatus.SPECIAL)
                    status.set(EdgeStatus.INITIAL)
                if (tryNonBlockingAddEdge(u, v))
                    return
            }
        }
    }

    fun doAddEdge(u: Int, v: Int) { // under lock
        val edge = makeEdge(u, v)
        if (!levels[0].connectedSimple(u, v)) {
            levels[0].addEdge(u, v)
            // just set, because status is SPECIAL
            statuses[edge]!!.set(EdgeStatus.TREE_EDGE)
        } else {
            levels[0].node(u).updateNonTreeEdges {
                nonTreeEdges!!.push(edge)
            }
            levels[0].node(v).updateNonTreeEdges {
                nonTreeEdges!!.push(edge)
            }
            // just set, because status is SPECIAL
            statuses[edge]!!.set(EdgeStatus.NON_TREE_EDGE)
        }
    }

    fun tryNonBlockingAddEdge(u: Int, v: Int): Boolean {
        val edge = makeEdge(u, v)
        val status = statuses[edge]!!
        val level = levels[0]
        level.node(u).updateNonTreeEdges {
            nonTreeEdges!!.push(edge)
        }
        level.node(v).updateNonTreeEdges {
            nonTreeEdges!!.push(edge)
        }
        val root = level.root(u)
        // check whether there is a concurrent edge addition
        val removeEdgeOperation = root.removeEdgeOperation
        if (removeEdgeOperation != null) {
            // simple reads, because the only interesting case is when the replacement search
            // is not finished before the end of this code
            if (level.connectedSimple(u, v) && !level.connectedSimple(u, v, removeEdgeOperation.additionalRoot)) {
                // can be a replacement
                if (status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.SPECIAL)) {
                    // self fail, so that no one could add the edge
                    if (removeEdgeOperation.replacement.compareAndSet(NO_EDGE, edge))
                        return true
                    return false
                }
            }
        }
        // try to finish non-blocking addition
        if (connected(u, v) && status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.NON_TREE_EDGE))
            return true
        return false
    }

    override fun removeEdge(u: Int, v: Int) {
        while (true) {
            val edge = makeEdge(u, v)
            val status = statuses[edge]!!
            val currentStatus = status.get()
            if (currentStatus == EdgeStatus.TREE_EDGE) {
                lockComponents(u, v) {
                    doRemoveEdge(u, v)
                    return
                }
            } else {
                if (currentStatus == EdgeStatus.SPECIAL) continue // active wait until the edge becomes TREE_EDGE
                require(currentStatus == EdgeStatus.NON_TREE_EDGE) // can remove edge
                if (status.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.REMOVED))
                    return
            }
        }

    }

    private fun doRemoveEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        val rank = ranks[edge]!!
        ranks.remove(edge)

        for (r in rank downTo 0) {
            // remove edge, but keep the parent link
            var (uRoot, vRoot) = levels[r].removeEdge(u, v, false)

            // swap components if needed, so that the uRoot component is smaller
            if (uRoot.size > vRoot.size) {
                val tmp = uRoot
                uRoot = vRoot
                vRoot = tmp
            }

            val lowerRoot = if (uRoot.parent != null) uRoot else vRoot

            if (r == 0) {
                // concurrent conflicts happen only on level 0
                // publish information about the operation
                val commonRoot = if (uRoot.parent != null) vRoot else uRoot
                val currentOperation = RemovalOperationInfo(u, v, lowerRoot)
                commonRoot.removeEdgeOperation = currentOperation

                // promote tree edges for less component
                increaseTreeEdgesRank(uRoot, u, v, r)
                findReplacement0(uRoot, lowerRoot, currentOperation)
                val replacementEdge: Edge = run {
                    val result = currentOperation.replacement.get()
                    if (result == NO_EDGE) {
                        // if is null then remove the opportunity for other threads to add a replacement
                        if (currentOperation.replacement.compareAndSet(NO_EDGE, 0L)) { // just try to CAS with something not NO_EDGE
                            return@run NO_EDGE
                        }  else {
                            // the failed CAS means that someone found a replacement
                            return@run currentOperation.replacement.get()
                        }
                    }
                    result
                }
                if (replacementEdge != NO_EDGE) {
                    statuses[replacementEdge]!!.set(EdgeStatus.TREE_EDGE)
                    for (i in r downTo 0) {
                        val lr = if (i == r) {
                            lowerRoot
                        } else {
                            val (ur, vr) = levels[i].removeEdge(u, v, false)
                            if (ur.parent != null) ur else vr
                        }

                        levels[i].addEdge(replacementEdge.u(), replacementEdge.v(), i == r, lr)
                    }
                    commonRoot.removeEdgeOperation = null
                    break
                } else {
                    // linearization point, do an actual split on this level
                    uRoot.parent = null
                    vRoot.parent = null
                    uRoot.version.inc()
                    vRoot.version.inc()
                }
                commonRoot.removeEdgeOperation = null
            } else {
                // promote tree edges for less component
                increaseTreeEdgesRank(uRoot, u, v, r)
                val replacementEdge = findReplacement(uRoot, r, lowerRoot)
                if (replacementEdge != NO_EDGE) {
                    statuses[replacementEdge]!!.set(EdgeStatus.TREE_EDGE)
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
                    uRoot.parent = null
                    vRoot.parent = null
                    uRoot.version.inc()
                    vRoot.version.inc()
                }
            }
        }
    }

    override fun connected(u: Int, v: Int) = levels[0].connected(u, v)

    private fun increaseTreeEdgesRank(node: Node, u: Int, v: Int, rank: Int) {
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
        node.recalculate()
    }

    private fun findReplacement(node: Node, rank: Int, additionalRoot: Node): Edge {
        if (!node.hasNonTreeEdges) return NO_EDGE

        val nonTreeEdges = node.nonTreeEdges

        var result: Edge = NO_EDGE

        nonTreeEdges?.let {
            while (true) {
                val edge = nonTreeEdges.pop()
                if (edge == NO_EDGE) break
                val edgeRank = ranks[edge] ?: continue // skip already deleted edges
                if (edgeRank != rank) continue // check that rank is correct, because we do not delete twin edges
                val status = statuses[edge]!!
                if (status.get() != EdgeStatus.NON_TREE_EDGE) continue // just remove deleted edges and continue

                if (!levels[rank].connectedSimple(edge.u(), edge.v(), additionalRoot)) {
                    // is replacement
                    if (status.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.TREE_EDGE)) {
                        result = edge
                    } else {
                        // the edge was deleted
                    }
                    break
                } else {
                    // promote non-tree edge
                    levels[rank + 1].node(edge.u()).updateNonTreeEdges {
                        this.nonTreeEdges!!.push(edge)
                    }
                    levels[rank + 1].node(edge.v()).updateNonTreeEdges {
                        this.nonTreeEdges!!.push(edge)
                    }
                    ranks[edge] = rank + 1
                }
            }
        }

        if (result == NO_EDGE) {
            val leftResult = node.left?.let { findReplacement(it, rank, additionalRoot) }
            if (leftResult != null)
                result = leftResult
        }
        if (result == NO_EDGE) {
            val rightResult = node.right?.let { findReplacement(it, rank, additionalRoot) }
            if (rightResult != null)
                result = rightResult
        }
        // recalculate flags after updates
        node.recalculate()
        return result
    }

    // level 0 is a special case
    private fun findReplacement0(node: Node, additionalRoot: Node, currentOperationInfo: RemovalOperationInfo): Boolean {
        if (!node.hasNonTreeEdges) return false

        val nonTreeEdges = node.nonTreeEdges
        var foundReplacement = false

        nonTreeEdges?.let {
            mainLoop@while (true) {
                val edge = nonTreeEdges.pop()
                if (edge == NO_EDGE) break
                val edgeRank = ranks[edge] ?: continue // skip already deleted edges
                if (edgeRank != 0) continue // check that rank is correct, because we do not delete twin edges
                when (statuses[edge]!!.get()) {
                    EdgeStatus.INITIAL -> {
                        // try to make a non-tree edge
                        val u = edge.u()
                        val v = edge.v()
                        if (levels[0].connectedSimple(u, v)) {
                            if (levels[0].node(u) == node)
                                levels[0].node(v).updateNonTreeEdges {
                                    this.nonTreeEdges!!.push(edge)
                                }
                            else
                                levels[0].node(u).updateNonTreeEdges {
                                    this.nonTreeEdges!!.push(edge)
                                }
                            statuses[edge]!!.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.NON_TREE_EDGE)
                            // one way or another the edge will be added by this point
                        } else {
                            if (statuses[edge]!!.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.SPECIAL)) {
                                // just skip
                                continue@mainLoop
                            } else {
                                // is not an initial now
                            }
                        }
                    }
                    EdgeStatus.TREE_EDGE -> continue@mainLoop // skip too
                    EdgeStatus.REMOVED -> continue@mainLoop // just remove
                    EdgeStatus.SPECIAL -> continue@mainLoop
                }

                // is a non tree edge here, or maybe deleted
                if (!levels[0].connectedSimple(edge.u(), edge.v(), additionalRoot)) {
                    // is replacement
                    if (statuses[edge]!!.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.TREE_EDGE)) {
                        if (currentOperationInfo.replacement.compareAndSet(NO_EDGE, edge)) {
                            // success
                        } else {
                            // an awkward situation.
                            // found a replacement edge, made it TREE, but someone found an another one.
                            // because the transition from TREE_EDGE to NON_TREE_EDGE is not allowed, we should use
                            // our edge as a replacement and add another edge as NON_TREE
                            val anotherEdge = currentOperationInfo.replacement.get()
                            val u = anotherEdge.u()
                            val v = anotherEdge.v()
                            levels[0].node(u).updateNonTreeEdges {
                                this.nonTreeEdges!!.push(anotherEdge)
                            }
                            levels[0].node(v).updateNonTreeEdges {
                                this.nonTreeEdges!!.push(anotherEdge)
                            }
                            statuses[anotherEdge]!!.set(EdgeStatus.NON_TREE_EDGE) // now it is a regular non-tree edge
                            currentOperationInfo.replacement.set(edge)
                        }

                        foundReplacement = true
                        break
                    } else {
                        // the edge was deleted
                    }
                } else {
                    // promote non-tree edge
                    levels[1].node(edge.u()).updateNonTreeEdges {
                        this.nonTreeEdges!!.push(edge)
                    }
                    levels[1].node(edge.v()).updateNonTreeEdges {
                        this.nonTreeEdges!!.push(edge)
                    }
                    ranks[edge] = 1
                }
            }
        }

        if (!foundReplacement) {
            val foundReplacementInLeft = node.left?.let { findReplacement0(it, additionalRoot, currentOperationInfo) } ?: false
            foundReplacement = foundReplacementInLeft
        }
        if (!foundReplacement) {
            val foundReplacementInRight = node.right?.let { findReplacement0(it, additionalRoot, currentOperationInfo) } ?: false
            foundReplacement = foundReplacementInRight
        }
        node.recalculate()
        return foundReplacement
    }

    private fun root(u: Int): Node = levels[0].root(u)

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