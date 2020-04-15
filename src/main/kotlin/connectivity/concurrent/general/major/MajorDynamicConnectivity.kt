package connectivity.concurrent.general.major

import connectivity.sequential.general.DynamicConnectivity
import org.cliffc.high_scale_lib.NonBlockingHashMap
import java.lang.Exception
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

val log = StringBuilder()

inline fun StringBuilder.synchronizedAppendln(text: String) {
    /*synchronized(this) {
        appendln(text)
    }*/
}

class MajorDynamicConnectivity(private val size: Int) : DynamicConnectivity {
    private val levels: Array<MajorConcurrentEulerTourTree>
    private val statuses = NonBlockingHashMap<Pair<Int, Int>, AtomicReference<EdgeStatus>>()
    private val ranks = NonBlockingHashMap<Pair<Int, Int>, Int>()

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
        val status = AtomicReference(EdgeStatus.INITIAL)
        statuses[edge] = status
        while (true) {
            val currentStatus = status.get()
            if (currentStatus != EdgeStatus.INITIAL && currentStatus != EdgeStatus.FAILED)
                return // someone already finished the edge addition
            if (!connected(u, v)) {
                if (currentStatus != EdgeStatus.FAILED) {
                    if (!status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.FAILED)) {
                        if (status.get() != EdgeStatus.FAILED)
                            return // someone already finished the edge addition
                    }
                }
                log.synchronizedAppendln("blocking add")
                lockComponents(u, v) {
                    doAddEdge(u, v)
                    return
                }
            } else {
                if (currentStatus == EdgeStatus.FAILED)
                    status.set(EdgeStatus.INITIAL)
                if (tryNonBlockingAddEdge(u, v))
                    return
            }
        }
    }

    fun doAddEdge(u: Int, v: Int) { // under lock
        val edge = Pair(min(u, v), max(u, v))
        if (!levels[0].connectedSimple(u, v)) {
            levels[0].addEdge(u, v)
            log.synchronizedAppendln("tree edge $u $v")
            statuses[edge]!!.set(EdgeStatus.TREE_EDGE)
        } else {
            log.synchronizedAppendln("non-tree edge $u $v")
            levels[0].node(u).updateNonTreeEdges {
                nonTreeEdges!!.push(edge)
            }
            levels[0].node(v).updateNonTreeEdges {
                nonTreeEdges!!.push(edge)
            }
            statuses[edge]!!.set(EdgeStatus.NON_TREE_EDGE)
        }
    }

    fun tryNonBlockingAddEdge(u: Int, v: Int): Boolean {
        val edge = Pair(min(u, v), max(u, v))
        val status = statuses[edge]!!
        log.synchronizedAppendln(status.get().toString())
        val level = levels[0]
        level.node(u).updateNonTreeEdges {
            nonTreeEdges!!.push(edge)
        }
        level.node(v).updateNonTreeEdges {
            nonTreeEdges!!.push(edge)
        }
        val root = level.root(u)
        log.synchronizedAppendln("read information from ${root}")
        val removeEdgeOperation = root.removeEdgeOperation
        if (removeEdgeOperation == null) {
            log.synchronizedAppendln("there is no parallel removeEdge operation")
        } else {
            log.synchronizedAppendln("parallel remove edge operation")
        }
        if (removeEdgeOperation != null) {
            if (level.connectedSimple(u, v) && !level.connectedSimple(u, v, removeEdgeOperation.additionalRoot)) {
                // can be a replacement
                log.synchronizedAppendln("can be a replacement")
                if (status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.FAILED)) {
                    // self fail so that no one could add
                    if (removeEdgeOperation.replacement.compareAndSet(null, edge)) {
                        log.synchronizedAppendln("concurrent add found a replacement edge (${edge.first}, ${edge.second})")
                        return true
                    }
                    return false
                }
            }
        }
        if (connected(u, v) && status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.NON_TREE_EDGE)) {
            log.synchronizedAppendln("non-blocking add self")
            return true
        }
        return false
    }

    override fun removeEdge(u: Int, v: Int) {
        while (true) {
            val edge = Pair(min(u, v), max(u, v))
            val status = statuses[edge]!!
            val currentStatus = status.get()
            if (currentStatus == EdgeStatus.TREE_EDGE) {
                log.synchronizedAppendln("blocking remove (${edge.first}, ${edge.second})")
                lockComponents(u, v) {
                    doRemoveEdge(u, v)
                    return
                }
            } else {
                if (currentStatus == EdgeStatus.FAILED) continue // active wait until the edge becomes TREE_EDGE
                log.synchronizedAppendln("non-blocking remove (${edge.first}, ${edge.second})")
                require(currentStatus == EdgeStatus.NON_TREE_EDGE) // can remove edge
                if (status.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.REMOVED))
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
                log.synchronizedAppendln("Published information to ${commonRoot}, other ${lowerRoot}")

                // promote tree edges for less component
                increaseTreeEdgesRank(uRoot, u, v, r)
                findReplacement0(uRoot, lowerRoot, currentOperation)
                val replacementEdge = run {
                    val result = currentOperation.replacement.get()
                    if (result == null) {
                        // if is null then remove the opportunity for other threads to add a replacement
                        if (currentOperation.replacement.compareAndSet(null, edge)) { // just try to CAS with something not null
                            return@run null
                        }  else {
                            return@run currentOperation.replacement.get()
                        }
                    }
                    result
                }
                if (replacementEdge != null) {
                    log.synchronizedAppendln("found a replacement")
                    log.synchronizedAppendln("replacement: ${replacementEdge.first} ${replacementEdge.second}")
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
                    commonRoot.removeEdgeOperation = null
                    break
                } else {
                    log.synchronizedAppendln("did not find a replacement")
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
                    log.synchronizedAppendln("found a replacement")
                    log.synchronizedAppendln("replacement: ${replacementEdge.first} ${replacementEdge.second}")
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
                    log.synchronizedAppendln("did not find a replacement")

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
                val status = statuses[edge]!!
                if (status.get() != EdgeStatus.NON_TREE_EDGE) continue // just remove deleted edges and continue

                if (!levels[rank].connectedSimple(edge.first, edge.second, additionalRoot)) {
                    // is replacement
                    if (status.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.TREE_EDGE)) {
                        result = edge
                    } else {
                        // the edge was deleted
                    }
                    break
                } else {
                    // promote non-tree edge
                    levels[rank + 1].node(edge.first).updateNonTreeEdges {
                        this.nonTreeEdges!!.push(edge)
                    }
                    levels[rank + 1].node(edge.second).updateNonTreeEdges {
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
        //if (!node.hasNonTreeEdges) return false

        val nonTreeEdges = node.nonTreeEdges
        var foundReplacement = false

        nonTreeEdges?.let {
            mainLoop@while (true) {
                val edge = nonTreeEdges.pop() ?: break
                log.synchronizedAppendln("traversed an edge (${edge.first}, ${edge.second})")
                val edgeRank = ranks[edge] ?: continue // skip already deleted edges
                if (edgeRank != 0) continue // check that rank is correct, because we do not delete twin edges
                log.synchronizedAppendln(statuses[edge]!!.get().toString())
                when (statuses[edge]!!.get()) {
                    EdgeStatus.INITIAL -> {
                        // try to make a non-tree edge
                        val (u, v) = edge
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
                            log.synchronizedAppendln("edge addition by a replacing thread (${edge.first}, ${edge.second})")
                        } else {
                            if (statuses[edge]!!.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.FAILED)) {
                                // just skip
                                continue@mainLoop
                            } else {
                                // is not an initial now
                            }
                        }
                    }
                    EdgeStatus.TREE_EDGE -> continue@mainLoop // skip too
                    EdgeStatus.REMOVED -> continue@mainLoop // just remove
                    EdgeStatus.FAILED -> continue@mainLoop
                }

                // is a non tree edge here, or maybe deleted
                if (!levels[0].connectedSimple(edge.first, edge.second, additionalRoot)) {
                    // is replacement
                    if (statuses[edge]!!.compareAndSet(EdgeStatus.NON_TREE_EDGE, EdgeStatus.TREE_EDGE)) {
                        if (currentOperationInfo.replacement.compareAndSet(null, edge)) {
                            // success
                            log.synchronizedAppendln("remove found a replacement edge (${edge.first}, ${edge.second})")
                        } else {
                            log.synchronizedAppendln("awkward situation (${edge.first}, ${edge.second}) (${currentOperationInfo.replacement.get().first}, ${currentOperationInfo.replacement.get().second})")
                            // an awkward situation.
                            // found a replacement edge, made it tree, but somebody found an another one.
                            // because the transition from TREE_EDGE to NON_TREE_EDGE is not allowed, we should use
                            // our edge as a replacement and handle another edge
                            val anotherEdge = currentOperationInfo.replacement.get()
                            val (u, v) = anotherEdge
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
                    levels[1].node(edge.first).updateNonTreeEdges {
                        this.nonTreeEdges!!.push(edge)
                    }
                    levels[1].node(edge.second).updateNonTreeEdges {
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