package benchmarks

import benchmarks.util.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioGeneratorTest {
    @Test
    fun test() {
        val edges = longArrayOf(
            bidirectionalEdge(0, 1),
            bidirectionalEdge(0, 2),
            bidirectionalEdge(0, 4),
            bidirectionalEdge(1, 2),
            bidirectionalEdge(1, 4),
            bidirectionalEdge(1, 5),
            bidirectionalEdge(2, 3),
            bidirectionalEdge(2, 4),
            bidirectionalEdge(2, 7),
            bidirectionalEdge(3, 5),
            bidirectionalEdge(3, 7),
            bidirectionalEdge(3, 8),
            bidirectionalEdge(4, 5),
            bidirectionalEdge(5, 7),
            bidirectionalEdge(5, 8),
            bidirectionalEdge(6, 7),
            bidirectionalEdge(7, 8)
        )

        val graph = Graph(9, edges)
        val scenario = ScenarioGenerator().generate(graph, 3, 6, 1, 1)
        assertEquals(8, scenario.initialEdges.size)
        assertEquals(3, scenario.queries.size)
        assertEquals(6, scenario.queries[0].size)
        assertTrue(scenario.queries.any { qs -> qs.any { it.type() == QueryType.CONNECTED } })
        assertTrue(scenario.queries.any { qs -> qs.any { it.type() == QueryType.ADD_EDGE } })
        assertTrue(scenario.queries.any { qs -> qs.any { it.type() == QueryType.REMOVE_EDGE } })
    }
}