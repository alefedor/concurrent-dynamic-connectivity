package connectivity.concurrent.general.major_coarse_grained

import connectivity.*
import connectivity.concurrent.general.major.*
import connectivity.concurrent.general.major_coarse_grained.recalculateNonTreeEdges
import connectivity.concurrent.general.major_coarse_grained.recalculateTreeEdges
import connectivity.concurrent.general.major_coarse_grained.updateNonTreeEdges
import connectivity.sequential.general.DynamicConnectivity
import java.lang.IllegalStateException

class MajorCoarseGrainedDynamicConnectivity(private val size: Int) : DynamicConnectivity {
    private val levels: Array<MajorConcurrentEulerTourTree>
    private val states = ConcurrentEdgeMap<EdgeState>()

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
        // use random bits instead of rank to avoid the ABA problem
        var initialState = makeState(INITIAL, randomBits())
        val previousState = states.putIfAbsent(edge, initialState)
        if (previousState != null) {
            if (previousState.status() != INITIAL) {
                if (previousState.status() == SPANNING_IN_PROGRESS)
                    synchronize()
                return // the edge is already present
            } else
                initialState = previousState // help to add an edge for a concurrent addition
        }
        while (true) {
            if (!levels[0].connectedSimple(u, v)) {
                doAddEdge(u, v, initialState)
                return
            } else {
                if (tryNonBlockingAddEdge(u, v, initialState))
                    return
            }
            val currentState = states[edge] ?: return
            if (currentState != initialState) {
                if (currentState.status() == SPANNING_IN_PROGRESS) {
                    synchronize()
                }
                return // someone already finished the edge addition
            }
        }
    }

    @Synchronized
    fun synchronize() {

    }

    @Synchronized
    fun doAddEdge(u: Int, v: Int, initialState: Int) { // under lock
        val edge = makeEdge(u, v)
        if (states[edge] ?: -1 != initialState) return
        if (!levels[0].connectedSimple(u, v)) {
            states.put(edge, makeState(SPANNING_IN_PROGRESS, 0))
            levels[0].addEdge(u, v)
            states.put(edge, makeState(SPANNING, 0))
        } else {
            val uNode = levels[0].node(u)
            val vNode = levels[0].node(v)
            addInfo(uNode, vNode, edge)
            if (!states.replace(edge, initialState, makeState(NON_SPANNING, 0))) {
                // could not add a non-spanning edge => delete added information
                removeInfo(uNode, vNode, edge)
            }
        }
    }

    private fun proposeReplacement(operationInfo: RemovalOperationInfo, edgeWithState: Long): Boolean {
        while (true) {
            val currentReplacement = operationInfo.replacement.value

            when (currentReplacement) {
                CLOSED -> return false
                NO_EDGE -> {
                    if (operationInfo.replacement.compareAndSet(NO_EDGE, edgeWithState))
                        return true
                }
                edgeWithState -> return true
                else -> {
                    val replacementEdge = currentReplacement.edge()
                    val initialState = currentReplacement.state()
                    if (initialState == 0) return false // is an edge proposed by the operation itself
                    val nextState = makeState(SPANNING, 0)
                    if (states.replace(replacementEdge, initialState, nextState) || (states[replacementEdge] ?: -1) == nextState)
                        return replacementEdge == edgeWithState.edge()
                    // remove the previous replacement as it was removed
                    operationInfo.replacement.compareAndSet(currentReplacement, NO_EDGE)
                }
            }
        }
    }

    fun tryNonBlockingAddEdge(u: Int, v: Int, initialState: Int): Boolean {
        val edge = makeEdge(u, v)
        val level = levels[0]
        val uNode = level.node(u)
        val vNode = level.node(v)
        addInfo(uNode, vNode, edge)
        val root = level.root(u)
        // check whether there is a concurrent edge addition
        val removeEdgeOperation = root.removeEdgeOperation
        if (removeEdgeOperation != null) {
            // simple reads, because the only interesting case is when the replacement search
            // is not finished before the end of this code
            if (level.connectedSimple(u, v) && !level.connectedSimple(u, v, removeEdgeOperation.additionalRoot)) {
                // can be a replacement.
                // propose the edge as a replacement
                val edgeWithState = pack(initialState, edge)
                if (proposeReplacement(removeEdgeOperation, edgeWithState)) {
                    states.replace(edge, initialState, makeState(SPANNING, 0))
                    removeInfo(uNode, vNode, edge)
                    return true
                } else {
                    if (removeEdgeOperation.replacement.value == CLOSED) {
                        // the edge is about to become spanning
                        doAddEdge(u, v, initialState)
                        return true
                    }
                    // there is a replacement => can continue safely
                }
            }
        }
        // try to finish non-blocking addition
        if (connected(u, v) && states.replace(edge, initialState, makeState(NON_SPANNING, 0))) {
            return true
        }
        removeInfo(uNode, vNode, edge)
        return false
    }

    override fun removeEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        while (true) {
            val currentState = states[edge] ?: return
            val currentStatus = currentState.status()
            when (currentStatus) {
                INITIAL -> return // no edge to remove
                SPANNING, SPANNING_IN_PROGRESS -> {
                    doRemoveEdge(u, v)
                    return
                }
                NON_SPANNING -> {
                    if (nonSpanningRemoveEdge(u, v, currentState, edge)) return
                }
            }
        }
    }

    private fun nonSpanningRemoveEdge(u: Int, v: Int, currentState: Int, edge: Long): Boolean {
        // currentState.status() should be NON_SPANNING
        val currentRank = currentState.rank()
        if (states.removeIf(edge, currentState)) {
            removeInfo(levels[currentRank].node(u), levels[currentRank].node(v), edge)
            return true
        }
        return false
    }

    @Synchronized
    private fun doRemoveEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        val state = states[edge] ?: return
        if (state.status() == INITIAL) return
        if (state.status() == NON_SPANNING) {
            nonSpanningRemoveEdge(u, v, state, edge)
            return
        }
        val rank = state.rank()
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
                    if (proposeReplacement(currentOperation, CLOSED))
                        return@run NO_EDGE
                    currentOperation.replacement.value.edge()
                }
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
                    commonRoot.removeEdgeOperation = null
                    break
                } else {
                    // linearization point, do an actual split on this level
                    uRoot.version.inc()
                    vRoot.version.inc()
                    uRoot.parent = null
                    vRoot.parent = null
                }
                commonRoot.removeEdgeOperation = null
            } else {
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
                    // do an actual split on this level
                    uRoot.parent = null
                    vRoot.parent = null
                }
            }
        }
        states.removeIf(edge)
    }

    override fun connected(u: Int, v: Int) = levels[0].connected(u, v)

    private fun increaseTreeEdgesRank(node: Node, u: Int, v: Int, rank: Int) {
        if (!node.hasCurrentLevelTreeEdges) return

        val treeEdge = node.currentLevelTreeEdge
        if (treeEdge != NO_EDGE) {
            node.currentLevelTreeEdge = NO_EDGE
            levels[rank + 1].addEdge(treeEdge.u(), treeEdge.v())
            // state should be (SPANNING, rank) here
            states.put(treeEdge, makeState(SPANNING, rank + 1))
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

    private fun findReplacement(node: Node, rank: Int, additionalRoot: Node): Edge {
        if (!node.hasNonTreeEdges) return NO_EDGE

        val nonTreeEdges = node.nonTreeEdges

        var result: Edge = NO_EDGE

        nonTreeEdges?.let {
            val iterator = it.iterator()
            while (iterator.hasNext()) {
                val edge = iterator.next()
                val edgeState = states[edge] ?: continue // skip already deleted edges
                if (edgeState.rank() != rank) continue // check that rank is correct
                val status = edgeState.status()
                if (status == SPANNING) throw IllegalStateException("A spanning edge was not fully deleted from non-spanning sets")
                if (status != NON_SPANNING) continue // skip any non-spanning edges
                if (!levels[rank].connectedSimple(edge.u(), edge.v(), additionalRoot)) {
                    // is a replacement
                    if (states.replace(edge, edgeState, makeState(SPANNING, rank))) {
                        removeInfo(levels[rank].node(edge.u()), levels[rank].node(edge.v()), edge)
                        result = edge
                        break
                    } else {
                        // the edge was removed
                    }
                } else {
                    // promote non-tree edge
                    addInfo(levels[rank + 1].node(edge.u()), levels[rank + 1].node(edge.v()), edge)
                    if (states.replace(edge, edgeState, makeState(NON_SPANNING, rank + 1))) {
                        // promotion is successful
                        // just remove info from the previous level
                        removeInfo(levels[rank].node(edge.u()), levels[rank].node(edge.v()), edge)
                    } else {
                        // promotion failed
                        // cancel the additions
                        removeInfo(levels[rank + 1].node(edge.u()), levels[rank + 1].node(edge.v()), edge)
                    }
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
        node.recalculateNonTreeEdges()
        return result
    }

    // level 0 is a special case
    private fun findReplacement0(node: Node, additionalRoot: Node, currentOperationInfo: RemovalOperationInfo): Boolean {
        if (!node.hasNonTreeEdges) return false
        val nonTreeEdges = node.nonTreeEdges

        // just an optimization check
        if (nonTreeEdges != null && nonTreeEdges.isNotEmpty()
            && currentOperationInfo.replacement.value != NO_EDGE
            && states[currentOperationInfo.replacement.value.edge()]?.status() == SPANNING) return true

        var foundReplacement = false

        nonTreeEdges?.let {
            val iterator = it.iterator()
            mainLoop@while (iterator.hasNext()) {
                val edge = iterator.next()
                var edgeState = states[edge] ?: continue // skip already deleted edges
                val edgeStatus = edgeState.status()
                val edgeRank = edgeState.rank()
                if (edgeRank != 0 && edgeStatus != INITIAL) continue // check that rank is correct
                when (edgeState.status()) {
                    INITIAL -> {
                        // the edge was not added yet
                        val u = edge.u()
                        val v = edge.v()
                        // check if can make the edge non-spanning
                        if (levels[0].connectedSimple(u, v)) {
                            if (!levels[0].connectedSimple(u, v, additionalRoot)) {
                                // can be a replacement
                                val edgeWithState = pack(edgeState, edge)
                                if (proposeReplacement(currentOperationInfo, edgeWithState)) {
                                    if (states.replace(edge, edgeState, makeState(SPANNING, 0)))
                                        break
                                }
                            }

                            // add info about the edge
                            addInfo(levels[0].node(u), levels[0].node(v), edge)
                            if (states.replace(edge, edgeState, makeState(NON_SPANNING, 0))) {
                                // the edge was successfully added by this thread
                            } else {
                                // the edge was added by another thread
                                // cancel the additions
                                removeInfo(levels[0].node(u), levels[0].node(v), edge)
                            }
                            // one way or another the edge will be added by this point
                        } else {
                            // the edge should be spanning and thus can not be a replacement
                            continue@mainLoop
                        }
                    }
                    SPANNING -> {
                        continue
                    }
                }
                // expect that the status is NON_SPANNING now
                edgeState = makeState(NON_SPANNING, 0)
                if (!levels[0].connectedSimple(edge.u(), edge.v(), additionalRoot)) {
                    // can be a replacement
                    if (states.replace(edge, edgeState, makeState(SPANNING, 0))) {
                        if (proposeReplacement(currentOperationInfo, pack(0, edge))) {
                            // success
                            removeInfo(levels[0].node(edge.u()), levels[0].node(edge.v()), edge)
                        } else {
                            // return to the previous state
                            check(states.replace(edge, makeState(SPANNING, 0), edgeState))
                        }
                        foundReplacement = true
                        break
                    } else {
                        // the edge was removed
                    }
                } else {
                    // promote non-tree edge
                    addInfo(levels[1].node(edge.u()), levels[1].node(edge.v()), edge)
                    if (states.replace(edge, edgeState, makeState(NON_SPANNING, 1))) {
                        // promotion is successful
                        // just remove info from the previous level
                        removeInfo(levels[0].node(edge.u()), levels[0].node(edge.v()), edge)
                    } else {
                        // promotion failed
                        // cancel the additions
                        removeInfo(levels[1].node(edge.u()), levels[1].node(edge.v()), edge)
                    }
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
        node.recalculateNonTreeEdges()
        return foundReplacement
    }

    private fun root(u: Int): Node = levels[0].root(u)

    private inline fun removeInfo(uNode: Node, vNode: Node, edge: Long) {
        uNode.nonTreeEdges!!.remove(edge)
        vNode.nonTreeEdges!!.remove(edge)
    }

    private inline fun addInfo(uNode: Node, vNode: Node, edge: Long) {
        uNode.updateNonTreeEdges {
            nonTreeEdges!!.add(edge)
        }
        vNode.updateNonTreeEdges {
            nonTreeEdges!!.add(edge)
        }
    }
}