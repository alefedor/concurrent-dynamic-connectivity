package benchmarks.util

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.RuntimeException
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

fun randomGraph(nodes: Int, edges: Int, rnd: Random = Random(0)): Graph {
    val edgesList = mutableListOf<Long>()
    val presentEdges = LongOpenHashSet()
    repeat(edges) {
        while (true) {
            val first = rnd.nextInt(nodes)
            val second = rnd.nextInt(nodes)
            if (first == second) continue
            if (presentEdges.contains(
                    bidirectionalEdge(
                        min(first, second),
                        max(first, second)
                    )
                )) continue
            edgesList.add(bidirectionalEdge(first, second))
            presentEdges.add(bidirectionalEdge(first, second))
            presentEdges.add(bidirectionalEdge(second, first))
            break
        }
    }
    return Graph(nodes, edgesList.toLongArray())
}

fun randomDividedGraph(components: Int, nodesEach: Int, edgesEach: Int, rnd: Random = Random(0)): Graph {
    val graphs = Array(components) { randomGraph(nodesEach, edgesEach, rnd) }
    val edges = LongArray(edgesEach * components) {
        val graphId = it / edgesEach
        val edgeId = it % edgesEach
        val edge = graphs[graphId].edges[edgeId]
        bidirectionalEdge(edge.from() + graphId * nodesEach, edge.to() + graphId * nodesEach)
    }
    return Graph(nodesEach * components, edges)
}

fun downloadOrCreateAndParseGraph(name: String, type: String, url: String): Graph {
    val gz = type.endsWith("gz")
    val ext = type.split(" ")[0]
    val graphFile = "$name." + (if (ext.startsWith("rand")) "gr" else ext) + (if (gz) ".gz" else "")
    if (!Paths.get(graphFile).toFile().exists()) {
        if (ext == "rand") {
            val parts = url.split(" ")
            val n = parts[0].toInt()
            val m = parts[1].toInt()
            println("Generating $graphFile as a random graph with $n nodes and $m edges")
            val graphNodes = randomGraph(n, m)
            writeGrFile(graphFile, graphNodes)
            println("Generated $graphFile")
        } else if (ext == "rand_divided") {
            val parts = url.split(" ")
            val components = parts[0].toInt()
            val n = parts[1].toInt()
            val m = parts[2].toInt()
            println("Generating $graphFile as a random divided graph with $components components, $n nodes and $m edges each")
            val graphNodes = randomDividedGraph(components, n, m)
            writeGrFile(graphFile, graphNodes)
            println("Generated $graphFile")
        } else {
            println("Downloading $graphFile from $url")
            val input = Channels.newChannel(URL(url).openStream())
            val output = FileOutputStream(graphFile)
            output.channel.transferFrom(input, 0, Long.MAX_VALUE)
            input.close()
            output.close()
            println("Downloaded $graphFile")
        }
    }
    val graph =  when {
        ext.startsWith("rand") || ext == "gr" -> parseGrFile(graphFile, gz)
        ext == "txt" -> parseTxtFile(graphFile, gz)
        else -> error("Unknown graph type: $ext")
    }

    val rnd = Random(454)
    return Graph(
        graph.nodes,
        graph.edges.toMutableList().shuffled(rnd).toLongArray()
    )
}

fun writeGrFile(filename: String, graph: Graph) {
    val m = graph.edges.size

    PrintWriter(filename).use { pw ->
        pw.println("p sp ${graph.nodes} $m")
        graph.edges.forEach { edge ->
            pw.println("a ${edge.from() + 1} ${edge.to() + 1} ${0}")
        }
    }
}

fun parseGrFile(filename: String, gziped: Boolean): Graph {
    val edges = mutableListOf<Long>()
    val inputStream = if (gziped) GZIPInputStream(FileInputStream(filename)) else FileInputStream(filename)
    var nodes: Int? = null

    InputStreamReader(inputStream).buffered().useLines { it.forEach { line ->
        when {
            line.startsWith("c ") -> {} // just ignore
            line.startsWith("p sp ") -> {
                nodes = line.split(" ")[2].toInt()
            }
            line.startsWith("a ") -> {
                val parts = line.split(" ")
                val from = parts[1].toInt() - 1
                val to = parts[2].toInt() - 1
                edges.add(bidirectionalEdge(from, to))
            }
        }
    }
    }
    return Graph(nodes!!, edges.toLongArray())
}

fun parseTxtFile(filename: String, gziped: Boolean): Graph {
    val edges = mutableListOf<Long>()
    val inputStream = if (gziped) GZIPInputStream(FileInputStream(filename)) else FileInputStream(filename)
    var nodes = 0
    InputStreamReader(inputStream).buffered().useLines { it.forEach { line ->
        when {
            line.startsWith("# ") -> {} // just ignore
            else -> {
                val parts = line.split(" ", "\t")
                val from = parts[0].toInt()
                val to   = parts[1].toInt()
                nodes = max(nodes, from + 1)
                nodes = max(nodes, to + 1)
                edges.add(bidirectionalEdge(from, to))
            }
        }
    }
    }
    return Graph(nodes, edges.toLongArray())
}

fun <T> addTrivialParameter(f: (Int) -> T): (Int, Int) -> T = { size, threads -> f(size) }