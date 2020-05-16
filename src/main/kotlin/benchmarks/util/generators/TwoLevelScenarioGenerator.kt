package benchmarks.util.generators

import benchmarks.util.*
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.random.Random

class TwoLevelScenarioGenerator {
    private val rnd = Random(295)
    private val randomScenarioGenerator = RandomScenarioGenerator()

    fun generate(components: Int, nodesPerComponent: Int, threads: Int, sizePerThread: Int): Scenario {
        val presentEdges = LongOpenHashSet()

        val interEdges = LongArray(4 * components) {
            val result: Long
            while (true) {
                val edge = randomInterEdge(components, nodesPerComponent)
                if (!presentEdges.contains(edge)) {
                    presentEdges.add(bidirectionalEdge(edge.from(), edge.to()))
                    presentEdges.add(bidirectionalEdge(edge.to(), edge.from()))
                    result = edge
                    break
                }
            }
            result
        }

        val interGraph = Graph(nodesPerComponent * components, interEdges)
        val scenario = randomScenarioGenerator.generate(interGraph, threads, sizePerThread, 1, 0, true)

        val initialEdges = LongArray(components * nodesPerComponent * (nodesPerComponent - 1) / 2 + scenario.initialEdges.size)
        var pos = 0
        repeat(components) {
            for (u in 0 until nodesPerComponent)
                for (v in (u + 1) until nodesPerComponent)
                    initialEdges[pos++] = bidirectionalEdge(
                        nodesPerComponent * it + u,
                        nodesPerComponent * it + v
                    )
        }
        for (edge in scenario.initialEdges)
            initialEdges[pos++] = edge
        check(pos == initialEdges.size)

        return Scenario(
            nodesPerComponent * components,
            threads,
            initialEdges,
            scenario.queries
        )
    }

    private fun randomInterEdge(components: Int, nodesPerComponent: Int): Long {
        while (true) {
            val firstComponent = rnd.nextInt(components)
            val secondComponent = rnd.nextInt(components)
            if (firstComponent != secondComponent)
                return bidirectionalEdge(
                    firstComponent * nodesPerComponent + rnd.nextInt(nodesPerComponent),
                    secondComponent * nodesPerComponent + rnd.nextInt(nodesPerComponent)
                )
        }
    }
}