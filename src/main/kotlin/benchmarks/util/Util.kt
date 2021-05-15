package benchmarks.util

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.*
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
    val bz2 = type.endsWith("bz2")
    val ext = type.split(" ")[0]
    val graphFile = "$name." + (if (ext.startsWith("rand")) "gr" else ext) + when {
        gz -> ".gz"
        bz2 -> ".bz2"
        else -> ""
    }
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
        ext == "graph" -> parseGraphFile(graphFile, bz2)
        else -> error("Unknown graph type: $ext")
    }
    check(graph.nodes <= (1 shl MAX_BITS_PER_NODE)) { "The maximum number of vertices in a graph should not be greater than 2^${MAX_BITS_PER_NODE}" }
    return Graph(
        graph.nodes,
        removeSameEdges(graph.edges)
    )
}

fun removeSameEdges(edges: LongArray): LongArray {
    val set = LongOpenHashSet()
    for (edge in edges)
        set.add(bidirectionalEdge(min(edge.from(), edge.to()), max(edge.from(), edge.to())))
    val result = LongArray(set.size)
    var pos = 0
    val iterator = set.iterator()
    val rnd = Random(435)
    while (iterator.hasNext()) {
        val edge = iterator.nextLong()
        if (rnd.nextBoolean())
            result[pos++] = bidirectionalEdge(edge.from(), edge.to())
        else
            result[pos++] = bidirectionalEdge(edge.to(), edge.from())
    }
    check(pos == result.size)
    return result
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
    val edges = LongArrayList()
    val inputStream = if (gziped) GZIPInputStream(FileInputStream(filename)) else FileInputStream(filename)
    var nodes: Int? = null

    InputStreamReader(inputStream).buffered().useLines { it.forEach { line ->
        when {
            line.startsWith("c ") -> {} // just ignore
            line.startsWith("p sp ") -> {
                nodes = line.split(' ')[2].toInt()
            }
            line.startsWith("a ") -> {
                val parts = line.split(' ')
                val from = parts[1].toInt() - 1
                val to = parts[2].toInt() - 1
                edges.add(bidirectionalEdge(from, to))
            }
        }
    }
    }
    edges.shuffle()
    return Graph(nodes!!, edges.toLongArray())
}

fun parseTxtFile(filename: String, gziped: Boolean): Graph {
    val edges = LongArrayList()
    val inputStream = if (gziped) GZIPInputStream(FileInputStream(filename)) else FileInputStream(filename)
    val idMapper = Int2IntOpenHashMap()

    InputStreamReader(inputStream).buffered().useLines { it.forEach { line ->
        when {
            line.startsWith("# ") -> {} // just ignore
            else -> {
                val parts = line.split(' ', '\t')
                val from = parts[0].toInt()
                val to   = parts[1].toInt()

                if (from != to) {
                    if (!idMapper.containsKey(from))
                        idMapper[from] = idMapper.size

                    if (!idMapper.containsKey(to))
                        idMapper[to] = idMapper.size

                    edges.add(bidirectionalEdge(idMapper[from], idMapper[to]))
                }
            }
        }
    }
    }
    edges.shuffle()
    return Graph(idMapper.size, edges.toLongArray())
}

fun parseGraphFile(filename: String, bz2: Boolean): Graph {
    val edges = LongArrayList()
    val input = BufferedInputStream(FileInputStream(filename))
    val inputStream = if (bz2) BZip2CompressorInputStream(input) else input
    var nodes: Int? = null

    var nodeId = -1
    InputStreamReader(inputStream).buffered().useLines { it.forEach { line ->
        nodeId++
        if (nodeId == 0) {
            nodes = line.split(' ')[0].toInt()
        } else {
            val neighbours = line.split(' ')
            neighbours.forEach {
                if (!it.isEmpty()) {
                    val neighbour = it.toInt()
                    edges += bidirectionalEdge(nodeId - 1, neighbour - 1)
                }
            }
        }
    }
    }
    edges.shuffle()
    return Graph(nodes!!, edges.toLongArray())
}

private fun LongArrayList.shuffle() {
    val rnd = Random(454)
    for (i in 0 until size) {
        val r = rnd.nextInt(i + 1)
        if (r != i) {
            val tmpr = getLong(i)
            set(i, getLong(r))
            set(r, tmpr)
        }
    }
}