package connectivity.concurrent.general.major

import connectivity.*
import connectivity.concurrent.general.major_coarse_grained.EdgeStatus
import connectivity.sequential.general.DynamicConnectivity
import java.io.File
import java.lang.IllegalStateException
import java.lang.StringBuilder

class MajorDynamicConnectivity(private val size: Int) : DynamicConnectivity {
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
        states[edge] = makeState(INITIAL, 0)
        var currentStatus = INITIAL
        while (true) {
            if (currentStatus != INITIAL) {
                //appendInLog("current status is $currentStatus! $u $v")
                return // someone already finished the edge addition
            }
            if (!connected(u, v)) {
                //appendInLog("addition spanning $u $v")
                withLockedComponents(u, v) {
                    if (doAddEdge(u, v))
                        return
                }
            } else {
                //appendInLog("addition non-spanning $u $v")
                if (tryNonBlockingAddEdge(u, v))
                    return
            }
            currentStatus = states[edge].status()
        }
    }

    fun doAddEdge(u: Int, v: Int): Boolean { // under lock
        val edge = makeEdge(u, v)
        if (!levels[0].connectedSimple(u, v)) {
            levels[0].addEdge(u, v)
            // just set, because no one else can concurrently change it
            states[edge] = makeState(SPANNING, 0)
            return true
        }
        return false
    }

    fun tryNonBlockingAddEdge(u: Int, v: Int): Boolean {
        val edge = makeEdge(u, v)
        val initialState = makeState(INITIAL, 0)
        val level = levels[0]
        level.node(u).updateNonTreeEdges(true) {
            nonTreeEdges!!.add(edge)
        }
        level.node(v).updateNonTreeEdges(true) {
            nonTreeEdges!!.add(edge)
        }
        val root = level.root(u)
        // check whether there is a concurrent edge addition
        val removeEdgeOperation = root.removeEdgeOperation
        if (removeEdgeOperation != null) {
            // simple reads, because the only interesting case is when the replacement search
            // is not finished before the end of this code
            if (level.connectedSimple(u, v) && !level.connectedSimple(u, v, removeEdgeOperation.additionalRoot)) {
                // can be a replacement
                if (states.replace(edge, initialState, makeState(REPLACEMENT, 0))) {
                    // self fail, so that no one could add the edge
                    if (removeEdgeOperation.replacement.compareAndSet(NO_EDGE, edge)) {
                        return true
                    }

                    level.node(u).updateNonTreeEdges(true) {
                        nonTreeEdges!!.remove(edge)
                    }
                    level.node(v).updateNonTreeEdges(true) {
                        nonTreeEdges!!.remove(edge)
                    }

                    states[edge] = initialState

                    return false
                }
            }
        }
        // try to finish non-blocking addition
        if (connected(u, v) && states.replace(edge, initialState, makeState(NON_SPANNING, 0))) {
            return true
        }

        level.node(u).updateNonTreeEdges(false) {
            nonTreeEdges!!.remove(edge)
        }
        level.node(v).updateNonTreeEdges(false) {
            nonTreeEdges!!.remove(edge)
        }
        return false
    }

    override fun removeEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        while (true) {
            val currentState = states[edge]!!
            val currentStatus = currentState.status()
            if (currentStatus == SPANNING) {
                withLockedComponents(u, v) {
                    doRemoveEdge(u, v)
                    return
                }
            } else {
                if (currentStatus == REPLACEMENT) continue // active wait until the edge becomes TREE_EDGE
                require(currentStatus == NON_SPANNING || currentStatus == REPLACEMENT) { "${currentStatus.status()} ${currentStatus.rank()}" } // can remove edge
                val currentRank = currentState.rank()
                if (states.replace(edge, currentState, makeState(REMOVED, currentState.rank()))) {
                    levels[currentRank].node(u).updateNonTreeEdges(false) {
                        nonTreeEdges!!.remove(edge)
                    }
                    levels[currentRank].node(v).updateNonTreeEdges(false) {
                        nonTreeEdges!!.remove(edge)
                    }
                    return
                }
            }
        }

    }

    private fun doRemoveEdge(u: Int, v: Int) {
        val edge = makeEdge(u, v)
        val state = states[edge]!!
        val rank = state.rank()

        states.remove(edge)

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
                    states[replacementEdge] = makeState(SPANNING, r)
                    // as the edge was NON_SPANNING before, its info should be removed
                    levels[r].node(u).updateNonTreeEdges(false) {
                        nonTreeEdges!!.remove(replacementEdge)
                    }
                    levels[r].node(v).updateNonTreeEdges(false) {
                        nonTreeEdges!!.remove(replacementEdge)
                    }
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
                    states[replacementEdge] = makeState(SPANNING, r)
                    // as the edge was NON_SPANNING before, its info should be removed
                    levels[r].node(u).updateNonTreeEdges(false) {
                        nonTreeEdges!!.remove(replacementEdge)
                    }
                    levels[r].node(v).updateNonTreeEdges(false) {
                        nonTreeEdges!!.remove(replacementEdge)
                    }
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
    }

    override fun connected(u: Int, v: Int) = levels[0].connected(u, v)

    private fun increaseTreeEdgesRank(node: Node, u: Int, v: Int, rank: Int) {
        if (!node.hasCurrentLevelTreeEdges) return

        val treeEdge = node.currentLevelTreeEdge
        if (treeEdge != NO_EDGE) {
            node.currentLevelTreeEdge = NO_EDGE
            levels[rank + 1].addEdge(treeEdge.u(), treeEdge.v())
            //require(states[treeEdge]!! == makeState(SPANNING, rank)) { "${states[treeEdge]?.status()} ${states[treeEdge]?.rank()}" } // for debug. TODO: remove
            states[treeEdge] = makeState(SPANNING, rank + 1)
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
                        result = edge
                    } else {
                        // the edge was removed
                    }
                    break
                } else {
                    // promote non-tree edge
                    levels[rank + 1].node(edge.u()).updateNonTreeEdges(true) {
                        this.nonTreeEdges!!.add(edge)
                    }
                    levels[rank + 1].node(edge.v()).updateNonTreeEdges(true) {
                        this.nonTreeEdges!!.add(edge)
                    }
                    if (states.replace(edge, edgeState, makeState(NON_SPANNING, rank + 1))) {
                        // promotion is successful
                        // just remove info from the previous level
                        levels[rank].node(edge.u()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
                        levels[rank].node(edge.v()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
                    } else {
                        // promotion failed
                        // cancel the additions
                        levels[rank + 1].node(edge.u()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
                        levels[rank + 1].node(edge.v()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
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
        if (nonTreeEdges != null && currentOperationInfo.replacement.get() != NO_EDGE) return true

        var foundReplacement = false

        nonTreeEdges?.let {
            val iterator = it.iterator()
            mainLoop@while (iterator.hasNext()) {
                val edge = iterator.next()
                //iterator.remove()
                var edgeState = states[edge] ?: continue // skip already deleted edges
                if (edgeState.rank() != 0) continue // check that rank is correct
                when (edgeState.status()) {
                    INITIAL -> {
                        // the edge was not added yet
                        val u = edge.u()
                        val v = edge.v()
                        // check if can make the edge non-spanning
                        if (levels[0].connectedSimple(u, v)) {
                            // add info about the edge
                            levels[0].node(v).updateNonTreeEdges(true) {
                                this.nonTreeEdges!!.add(edge)
                            }
                            levels[0].node(u).updateNonTreeEdges(true) {
                                this.nonTreeEdges!!.add(edge)
                            }
                            if (states.replace(edge, edgeState, makeState(NON_SPANNING, 0))) {
                                // the edge was successfully added by this thread
                            } else {
                                // the edge was added by another thread
                                // cancel the additions
                                levels[0].node(v).updateNonTreeEdges(false) {
                                    this.nonTreeEdges!!.remove(edge)
                                }
                                levels[0].node(u).updateNonTreeEdges(false) {
                                    this.nonTreeEdges!!.remove(edge)
                                }
                            }
                            // one way or another the edge will be added by this point
                        } else {
                            // the edge should be spanning and thus can not be a replacement
                            continue@mainLoop
                        }
                    }
                    SPANNING -> throw IllegalStateException("A spanning edge was not fully deleted from non-spanning sets")
                    REMOVED -> continue@mainLoop
                    REPLACEMENT -> continue@mainLoop
                }
                // expect that the state is NON_SPANNING now
                edgeState = makeState(NON_SPANNING, 0)
                // is a non-spanning or already removed edge here
                if (!levels[0].connectedSimple(edge.u(), edge.v(), additionalRoot)) {
                    // can be a replacement
                    if (states.replace(edge, edgeState, makeState(REPLACEMENT, 0))) {
                        if (currentOperationInfo.replacement.compareAndSet(NO_EDGE, edge)) {
                            // success
                        } else {
                            // somebody found a replacement before we did
                            // return the state of our replacement
                            states[edge] = edgeState
                        }

                        foundReplacement = true
                        break
                    } else {
                        // the edge was removed
                    }
                } else {
                    // promote non-tree edge
                    levels[1].node(edge.u()).updateNonTreeEdges(true) {
                        this.nonTreeEdges!!.add(edge)
                    }
                    levels[1].node(edge.v()).updateNonTreeEdges(true) {
                        this.nonTreeEdges!!.add(edge)
                    }
                    if (states.replace(edge, edgeState, makeState(NON_SPANNING, 1))) {
                        // promotion is successful
                        // just remove info from the previous level
                        levels[0].node(edge.u()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
                        levels[0].node(edge.v()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
                    } else {
                        // promotion failed
                        // cancel the additions
                        levels[1].node(edge.u()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
                        levels[1].node(edge.v()).updateNonTreeEdges(false) {
                            this.nonTreeEdges!!.remove(edge)
                        }
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

    private inline fun withLockedComponents(a: Int, b: Int, body: () -> Unit) {
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
            if (uRoot === vRoot) {
                synchronized(uRoot) {
                    if (uRoot == root(u)) {
                        body()
                        return
                    }
                }
            } else {
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
}