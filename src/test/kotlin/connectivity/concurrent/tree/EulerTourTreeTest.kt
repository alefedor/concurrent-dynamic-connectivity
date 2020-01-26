package connectivity.concurrent.tree

import org.junit.Assert.*
import org.junit.Test

class EulerTourTreeTest {
    @Test
    fun testCreation() {
        ConcurrentEulerTourTree(5)
    }

    @Test
    fun testAddEdge() {
        ConcurrentEulerTourTree(5).addTreeEdge(0, 1)
    }

    @Test
    fun testSameComponents() {
        val dc = ConcurrentEulerTourTree(5)
        assertFalse(dc.connected(0, 1))
        dc.addTreeEdge(0, 1)
        assertTrue(dc.connected(0, 1))
    }

    @Test
    fun testRemoveEdge() {
        val dc = ConcurrentEulerTourTree(5)
        dc.addTreeEdge(0, 1)
        dc.removeTreeEdge(1, 0)
        assertFalse(dc.connected(0, 1))
    }

    @Test
    fun testTransitivity() {
        val dc = ConcurrentEulerTourTree(5)
        dc.addTreeEdge(0, 1)
        dc.addTreeEdge(1, 2)
        for (i in 0 until 5)
            for (j in 0 until 5)
                assertEquals(i == j || (i <= 2 && j <= 2), dc.connected(i, j))
    }

    @Test
    fun testComplex() {
        val dc = ConcurrentEulerTourTree(5)
        dc.addTreeEdge(4, 0)
        dc.addTreeEdge(2, 0)
        dc.removeTreeEdge(0, 4)
        assertFalse(dc.connected(2, 4))
        dc.addTreeEdge(2, 4)
        assertTrue(dc.connected(2, 4))
        dc.removeTreeEdge(0, 2)
        assertFalse(dc.connected(4, 0))
    }
}