package connectivity.concurrent.general.major

import connectivity.sequential.general.DynamicConnectivity
import java.lang.IllegalStateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

class MajorDynamicConnectivity(private val size: Int) : DynamicConnectivity {
    private val levels: Array<MajorConcurrentEulerTourTree>
    private val statuses = ConcurrentHashMap<Pair<Int, Int>, AtomicReference<EdgeStatus>>()
    private val ranks = ConcurrentHashMap<Pair<Int, Int>, Int>()

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
        val edge = Pair(min(u, v), max(u, v))
        ranks[edge] = 0
        statuses[edge] = AtomicReference(EdgeStatus.INITIAL)
        while (true) {
            if (!connected(u, v)) {
                lockComponents(u, v) {
                    doAddEdge(u, v)
                    return
                }
            } else {
                if (tryNonBlockingAddEdge(u, v))
                    return
            }
        }
    }

    fun doAddEdge(u: Int, v: Int) { // under lock
        val edge = Pair(min(u, v), max(u, v))
        if (!levels[0].connectedSimple(u, v)) {
            levels[0].addEdge(u, v)
        } else {
            levels[0].node(u).update {
                nonTreeEdges!!.push(edge)
            }
            levels[0].node(v).update {
                nonTreeEdges!!.push(edge)
            }
        }
        statuses[edge]!!.set(EdgeStatus.TREE_EDGE)
    }

    fun tryNonBlockingAddEdge(u: Int, v: Int): Boolean {
        val edge = Pair(min(u, v), max(u, v))
        val level = levels[0]
        level.node(u).update {
            nonTreeEdges!!.push(edge)
        }
        level.node(v).update {
            nonTreeEdges!!.push(edge)
        }
        statuses[edge]!!.set(EdgeStatus.READY_TO_ADD) // only one thread can change the INITIAL status
        val removeEdgeOperation = level.root(u).removeEdgeOperation
        if (removeEdgeOperation != null) {
            if (!level.connectedSimple(removeEdgeOperation.u, removeEdgeOperation.v, removeEdgeOperation.additionalRoot)) {
                if (removeEdgeOperation.replacement.compareAndSet(null, edge))
                    return true
            }
        }
        if (connected(u, v) && statuses[edge]!!.compareAndSet(EdgeStatus.READY_TO_ADD, EdgeStatus.NON_TREE_EDGE))
            return true
        return false
    }

    override fun removeEdge(u: Int, v: Int) {
        while (true) {
            val edge = Pair(min(u, v), max(u, v))
            val status = statuses[edge]!!.get()
            if (status == EdgeStatus.TREE_EDGE) {
                lockComponents(u, v) {
                    doRemoveEdge(u, v)
                    return
                }
            } else {
                require(status == EdgeStatus.NON_TREE_EDGE) // can remove edge
                if (statuses[edge]!!.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.REMOVED))
                    return
            }
        }

    }

    private fun doRemoveEdge(u: Int, v: Int) {
        val edge = Pair(min(u, v), max(u, v))
        val rank = ranks[edge] ?: throw IllegalStateException()
        ranks.remove(edge)

        for (r in rank downTo 0) {
            var (uRoot, vRoot) = levels[r].removeEdge(u, v, false)

            if (uRoot.size > vRoot.size) {
                val tmp = uRoot
                uRoot = vRoot
                vRoot = tmp
            }

            val lowerRoot = if (uRoot.parent != null) uRoot else vRoot

            if (r == 0) {
                // publish information about the operation
                val commonRoot = if (uRoot.parent != null) vRoot else uRoot
                val currentOperation = RemovalOperationInfo(u, v, lowerRoot)
                commonRoot.removeEdgeOperation = currentOperation

                // promote tree edges for less component
                increaseTreeEdgesRank(uRoot, u, v, r)
                findReplacement0(uRoot, lowerRoot, currentOperation)
                val replacementEdge = run {
                    val result = currentOperation.replacement.get()
                    if (result == null) {
                        // if is null then remove the opportunity for other threads to add a replacement
                        if (currentOperation.replacement.compareAndSet(null, edge)) { // just try to CAS with something not null
                            return@run result
                        }  else {
                            return@run currentOperation.replacement.get()
                        }
                    }
                    result
                }
                if (replacementEdge != null) {
                    for (i in r downTo 0) {
                        val lr = if (i == r) {
                            lowerRoot
                        } else {
                            val (ur, vr) = levels[i].removeEdge(u, v, false)
                            if (ur.parent != null) ur else vr
                        }

                        levels[i].addEdge(replacementEdge.first, replacementEdge.second, i == r, lr)
                    }
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
                if (replacementEdge != null) {
                    statuses[replacementEdge]!!.set(EdgeStatus.TREE_EDGE)
                    for (i in r downTo 0) {
                        val lr = if (i == r) {
                            lowerRoot
                        } else {
                            val (ur, vr) = levels[i].removeEdge(u, v, false)
                            if (ur.parent != null) ur else vr
                        }

                        levels[i].addEdge(replacementEdge.first, replacementEdge.second, i == r, lr)
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

        node.currentLevelTreeEdge?.let {
            node.currentLevelTreeEdge = null
            if (it.first < it.second) { // not to promote the same edge twice
                levels[rank + 1].addEdge(it.first, it.second)
                ranks[it] = rank + 1
            }
        }

        node.left?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        node.right?.let {
            increaseTreeEdgesRank(it, u, v, rank)
        }

        node.recalculate()
    }

    private fun findReplacement(node: Node, rank: Int, additionalRoot: Node): Pair<Int, Int>? {
        if (!node.hasNonTreeEdges) return null

        val nonTreeEdges = node.nonTreeEdges

        var result: Pair<Int, Int>? = null

        nonTreeEdges?.let {
            while (true) {
                val edge = nonTreeEdges.pop() ?: break
                val edgeRank = ranks[edge] ?: continue // skip already deleted edges
                if (edgeRank != rank) continue // check that rank is correct, because we do not delete twin edges
                if (statuses[edge]!!.get() != EdgeStatus.NON_TREE_EDGE) continue // just remove deleted edges and continue

                if (!levels[rank].connectedSimple(edge.first, edge.second, additionalRoot)) {
                    // is replacement
                    result = edge
                    break
                } else {
                    // promote non-tree edge
                    levels[rank + 1].node(edge.first).update {
                        this.nonTreeEdges!!.push(edge)
                    }
                    levels[rank + 1].node(edge.second).update {
                        this.nonTreeEdges!!.push(edge)
                    }
                    ranks[edge] = rank + 1
                }
            }
        }

        if (result == null) {
            val leftResult = node.left?.let { findReplacement(it, rank, additionalRoot) }
            if (leftResult != null)
                result = leftResult
        }
        if (result == null) {
            val rightResult = node.right?.let { findReplacement(it, rank, additionalRoot) }
            if (rightResult != null)
                result = rightResult
        }
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
                val edge = nonTreeEdges.pop() ?: break
                val edgeRank = ranks[edge] ?: continue // skip already deleted edges
                if (edgeRank != 0) continue // check that rank is correct, because we do not delete twin edges
                when (statuses[edge]!!.get()) {
                    EdgeStatus.INITIAL -> continue@mainLoop // just skip
                    EdgeStatus.READY_TO_ADD -> {
                        // try to make a non-tree edge
                        val (u, v) = edge
                        if (connected(u, v) && statuses[edge]!!.compareAndSet(EdgeStatus.READY_TO_ADD, EdgeStatus.NON_TREE_EDGE)) {
                            // success
                        } else {
                            // just skip
                            continue@mainLoop
                        }
                    }
                    EdgeStatus.TREE_EDGE -> continue@mainLoop // skip too
                    EdgeStatus.REMOVED -> continue@mainLoop // just remove
                }

                // is a non tree edge here
                if (!levels[0].connectedSimple(edge.first, edge.second, additionalRoot)) {
                    // is replacement
                    if (statuses[edge]!!.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.TREE_EDGE)) {
                        if (currentOperationInfo.replacement.compareAndSet(null, edge)) {
                            // success
                        } else {
                            // an awkward situation.
                            // found a replacement edge, made it tree, but somebody found an another one.
                            // because the transition from TREE_EDGE to NON_TREE_EDGE is not allowed, we should use
                            // our edge as a replacement and handle another edge
                            val anotherEdge = currentOperationInfo.replacement.get()
                            statuses[anotherEdge]!!.set(EdgeStatus.NON_TREE_EDGE) // now it is a regular non-tree edge
                            currentOperationInfo.replacement.set(edge)
                        }

                        foundReplacement = true
                    }

                    break
                } else {
                    // promote non-tree edge
                    levels[1].node(edge.first).update {
                        this.nonTreeEdges!!.push(edge)
                    }
                    levels[1].node(edge.second).update {
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
        if (!foundReplacement && currentOperationInfo.replacement.get() == null) {
            val foundReplacementInRight = node.right?.let { findReplacement0(it, additionalRoot, currentOperationInfo) } ?: false
            foundReplacement = foundReplacementInRight
        }
        node.recalculate()
        return foundReplacement
    }

    private inline fun lockComponents(a: Int, b: Int, body: () -> Unit) {
        var u = a
        var v = b

        while (true) {
            var uRoot = levels[0].root(u)
            var vRoot = levels[0].root(v)

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
                    if (uRoot == levels[0].root(u) && vRoot == levels[0].root(v)) {
                        body()
                        return
                    }
                }
            }
        }
    }
}