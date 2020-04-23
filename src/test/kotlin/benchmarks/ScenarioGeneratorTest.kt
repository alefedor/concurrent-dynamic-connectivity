package benchmarks

import benchmarks.util.*
import connectivity.concurrent.general.CoarseGrainedLockingDynamicConnectivity
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ScenarioGeneratorTest {
    private val graph: Graph

    init {
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
        graph = Graph(9, edges)
    }

    @Test
    fun randomScenarioGeneratorTest() {
        val scenario = RandomScenarioGenerator().generate(graph, 3, 6, 1, 1)
        assertEquals(8, scenario.initialEdges.size)
        assertEquals(3, scenario.queries.size)
        assertEquals(6, scenario.queries[0].size)
        runScenario(scenario)
    }

    @Test
    fun incrementalScenarioGeneratorTest() {
        val scenario = IncrementalScenarioGenerator().generate(graph, 4)
        val edges = runScenario(scenario)
        assertEquals(16, edges.size)
    }

    @Test
    fun decrementalScenarioGeneratorTest() {
        val scenario = DecrementalScenarioGenerator().generate(graph, 4)
        val edges = runScenario(scenario)
        assertEquals(1, edges.size)
    }

    @Test
    fun twoLevelScenarioGeneratorTest() {
        val scenario = TwoLevelScenarioGenerator().generate(3, 6, 3, 6)
        runScenario(scenario)
        assertEquals(3 * 6, scenario.nodes)
        assertEquals(3 * (6 * 5) / 2 + 3 * 2, scenario.initialEdges.size)
        assertEquals(6, scenario.queries[0].size)
    }

    private fun runScenario(scenario: Scenario): Set<Long> {
        val set = LongOpenHashSet()
        scenario.initialEdges.forEach { set.add(normalizeEdge(it)) }
        scenario.queries.forEach { queries ->
            queries.forEach {
                val edge = normalizeEdge(bidirectionalEdge(it.from(), it.to()))
                when (it.type()) {
                    QueryType.ADD_EDGE -> {
                        assertFalse(set.contains(edge))
                        set.add(edge)
                    }
                    QueryType.REMOVE_EDGE -> {
                        assertTrue(set.contains(edge))
                        set.remove(edge)
                    }
                    QueryType.CONNECTED -> {}
                }
            }
        }
        return set
    }

    private fun normalizeEdge(edge: Long) = bidirectionalEdge(min(edge.from(), edge.to()), max(edge.from(), edge.to()))
}