package connectivity.concurrent.general.major

import connectivity.sequential.general.DynamicConnectivity
import org.cliffc.high_scale_lib.NonBlockingHashMap
import java.lang.Exception
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

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
        statuses[edge] = AtomicReference(EdgeStatus.INITIAL)
        while (true) {
            if (!connected(u, v)) {
                //println("blocking add")
                lockComponents(u, v) {
                    doAddEdge(u, v)
                    return
                }
            } else {
                //println("non-blocking add")
                println("non-tree $u $v")
                if (tryNonBlockingAddEdge(u, v))
                    return
            }
        }
    }

    fun doAddEdge(u: Int, v: Int) { // under lock
        val edge = Pair(min(u, v), max(u, v))
        if (!levels[0].connectedSimple(u, v)) {
            levels[0].addEdge(u, v)
            println("tree edge $u $v")
            statuses[edge]!!.set(EdgeStatus.TREE_EDGE)
        } else {
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
        //println(status.get())
        val level = levels[0]
        level.node(u).updateNonTreeEdges {
            nonTreeEdges!!.push(edge)
        }
        level.node(v).updateNonTreeEdges {
            nonTreeEdges!!.push(edge)
        }
        val root = level.root(u)
        //println("read information from ${root}")
        val removeEdgeOperation = root.removeEdgeOperation
        /*if (removeEdgeOperation == null)
            //println("there is no parallel removeEdge operation")
        else
            //println("parallel remove edge operation")*/
        if (removeEdgeOperation != null) {
            if (level.connectedSimple(u, v) && !level.connectedSimple(u, v, removeEdgeOperation.additionalRoot)) {
                // can be a replacement
                //println("can be a replacement")
                if (status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.FAILED)) {
                    // self fail so that no one could add
                    if (removeEdgeOperation.replacement.compareAndSet(null, edge)) {
                        //println("concurrent add found a replacement edge")
                        return true
                    }
                    //println("here")
                    status.set(EdgeStatus.INITIAL)
                    return false
                }
            }
        }
        if (connected(u, v) && status.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.NON_TREE_EDGE)) {
            //println("non-blocking add self")
            return true
        }
        val currentStatus = status.get()
        if (currentStatus != EdgeStatus.FAILED && currentStatus != EdgeStatus.INITIAL) return true // someone else added the edge
        if (currentStatus == EdgeStatus.FAILED)
            status.set(EdgeStatus.INITIAL)
        return false
    }

    override fun removeEdge(u: Int, v: Int) {
        while (true) {
            val edge = Pair(min(u, v), max(u, v))
            val status = statuses[edge]!!.get()
            if (status == EdgeStatus.TREE_EDGE) {
                //println("blocking remove")
                lockComponents(u, v) {
                    doRemoveEdge(u, v)
                    return
                }
            } else {
                if (status == EdgeStatus.FAILED) continue // active wait until the edge becomes TREE_EDGE
                //println("non-blocking remove")
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
                //println("Published information to ${commonRoot}, other ${lowerRoot}")

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
                    //println("found a replacement")
                    //println("replacement: ${replacementEdge.first} ${replacementEdge.second}")
                    statuses[replacementEdge]!!.set(EdgeStatus.TREE_EDGE)
                    for (i in r downTo 0) {
                        val lr = if (i == r) {
                            lowerRoot
                        } else {
                            val (ur, vr) = levels[i].removeEdge(u, v, false)
                            if (ur.parent != null) ur else vr
                        }

                        levels[i].addEdge(replacementEdge.first, replacementEdge.second, i == r, lr)
                        //println(connected(replacementEdge.first, replacementEdge.second))
                    }
                    commonRoot.removeEdgeOperation = null
                    break
                } else {
                    //println("did not find a replacement")
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
                    //println("found a replacement")

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
                    //println("did not find a replacement")

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
        if (!node.hasNonTreeEdges) return false

        val nonTreeEdges = node.nonTreeEdges
        var foundReplacement = false

        nonTreeEdges?.let {
            mainLoop@while (true) {
                val edge = nonTreeEdges.pop() ?: break
                //println("traversed an edge (${edge.first}, ${edge.second})")
                val edgeRank = ranks[edge] ?: continue // skip already deleted edges
                if (edgeRank != 0) continue // check that rank is correct, because we do not delete twin edges
                //println(statuses[edge]!!.get())
                when (statuses[edge]!!.get()) {
                    EdgeStatus.INITIAL -> {
                        // try to make a non-tree edge
                        val (u, v) = edge
                        if (levels[0].connectedSimple(u, v)) {
                            if (levels[0].node(u) == node)
                                levels[0].node(v).updateNonTreeEdges {
                                    nonTreeEdges!!.push(edge)
                                }
                            else
                                levels[0].node(u).updateNonTreeEdges {
                                    nonTreeEdges!!.push(edge)
                                }
                            statuses[edge]!!.compareAndSet(EdgeStatus.INITIAL, EdgeStatus.NON_TREE_EDGE)
                            // one way or another the edge will be added by this point
                            //println("edge addition by a replacing thread")
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
                            //println("remove found a replacement edge")
                        } else {
                            println("awkward situation")
                            // an awkward situation.
                            // found a replacement edge, made it tree, but somebody found an another one.
                            // because the transition from TREE_EDGE to NON_TREE_EDGE is not allowed, we should use
                            // our edge as a replacement and handle another edge
                            val anotherEdge = currentOperationInfo.replacement.get()
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
            synchronized(this) {
                //synchronized(vRoot) {
                    if (uRoot == levels[0].root(u) && vRoot == levels[0].root(v)) {
                        body()
                        return
                    }
                //}
            }
        }
    }
}