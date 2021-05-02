package benchmarks

import benchmarks.util.Graph
import benchmarks.util.downloadOrCreateAndParseGraph
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

val USA_ROADS_GRAPH_PARAMS = Triple("USA-ROADS", "gr gz", "http://users.diag.uniroma1.it/challenge9/data/USA-road-d/USA-road-d.COL.gr.gz")
val RANDOM_N_GRAPH_PARAMS = Triple("RANDOM-N", "rand", "400000 400000")
val RANDOM_2N_GRAPH_PARAMS = Triple("RANDOM-2N", "rand", "300000 600000")
val RANDOM_NLOG_GRAPH_PARAMS = Triple("RANDOM-NLOG", "rand", "100000 1600000")
val RANDOM_NSQRT_GRAPH_PARAMS = Triple("RANDOM-NSQRT", "rand", "20000 1600000")
val RANDOM_DIVIDED_GRAPH_PARAMS = Triple("RANDOM-DIVIDED", "rand_divided", "10 10000 160000")
val TWITTER_GRAPH_PARAMS = Triple("TWITTER", "txt gz", "http://snap.stanford.edu/data/twitter_combined.txt.gz")
val STANFORD_WEB_GRAPH_PARAMS = Triple("STANFORD-WEB", "txt gz", "http://snap.stanford.edu/data/web-Stanford.txt.gz")

private lateinit var USA_ROADS_GRAPH: Graph
private lateinit var RANDOM_N_GRAPH: Graph
private lateinit var RANDOM_2N_GRAPH: Graph
private lateinit var RANDOM_NLOG_GRAPH: Graph
private lateinit var RANDOM_NSQRT_GRAPH: Graph
private lateinit var RANDOM_DIVIDED_GRAPH: Graph
private lateinit var TWITTER_GRAPH: Graph
private lateinit var STANFORD_WEB_GRAPH: Graph

enum class GraphParams : Serializable {
    USA_ROADS,
    RANDOM_N,
    RANDOM_2N,
    RANDOM_NLOG,
    RANDOM_NSQRT,
    TWITTER,
    STANFORD_WEB,
    RANDOM_DIVIDED
}

fun loadGraph(params: Triple<String, String, String>): Graph {
    return loadGraph(params.first, params.second, params.third)
}

fun loadGraph(graphName: String, graphType: String, graphUrl: String): Graph {
    println("Loading $graphName $graphType")
    return downloadOrCreateAndParseGraph(graphName, graphType, graphUrl)
}


interface GraphServerInterface : Remote {
    @Throws(RemoteException::class)
    fun graphByParams(params: GraphParams): Graph
}

class GraphServer : UnicastRemoteObject(), GraphServerInterface {
    override fun graphByParams(params: GraphParams) = when (params) {
        GraphParams.RANDOM_DIVIDED -> {
            if (!::RANDOM_DIVIDED_GRAPH.isInitialized)
                RANDOM_DIVIDED_GRAPH = loadGraph(RANDOM_DIVIDED_GRAPH_PARAMS)
            RANDOM_DIVIDED_GRAPH
        }
        GraphParams.USA_ROADS -> {
            if (!::USA_ROADS_GRAPH.isInitialized)
                USA_ROADS_GRAPH = loadGraph(USA_ROADS_GRAPH_PARAMS)
            USA_ROADS_GRAPH
        }
        GraphParams.RANDOM_N -> {
            if (!::RANDOM_N_GRAPH.isInitialized)
                RANDOM_N_GRAPH = loadGraph(RANDOM_N_GRAPH_PARAMS)
            RANDOM_N_GRAPH
        }
        GraphParams.RANDOM_2N -> {
            if (!::RANDOM_2N_GRAPH.isInitialized)
                RANDOM_2N_GRAPH = loadGraph(RANDOM_2N_GRAPH_PARAMS)
            RANDOM_2N_GRAPH
        }
        GraphParams.RANDOM_NLOG -> {
            if (!::RANDOM_NLOG_GRAPH.isInitialized)
                RANDOM_NLOG_GRAPH = loadGraph(RANDOM_NLOG_GRAPH_PARAMS)
            RANDOM_NLOG_GRAPH
        }
        GraphParams.RANDOM_NSQRT -> {
            if (!::RANDOM_NSQRT_GRAPH.isInitialized)
                RANDOM_NSQRT_GRAPH = loadGraph(RANDOM_NSQRT_GRAPH_PARAMS)
            RANDOM_NSQRT_GRAPH
        }
        GraphParams.TWITTER -> {
            if (!::TWITTER_GRAPH.isInitialized)
                TWITTER_GRAPH = loadGraph(TWITTER_GRAPH_PARAMS)
            TWITTER_GRAPH
        }
        GraphParams.STANFORD_WEB -> {
            if (!::STANFORD_WEB_GRAPH.isInitialized)
                STANFORD_WEB_GRAPH = loadGraph(STANFORD_WEB_GRAPH_PARAMS)
            STANFORD_WEB_GRAPH
        }
    }

    companion object {
        var obj: GraphServer? = null
        const val NAME = "//localhost/GraphServer"

        @JvmStatic
        fun main(args: Array<String>) {
            val registry = LocateRegistry.createRegistry(1099)
            obj = GraphServer()
            registry.rebind(NAME, obj)
        }

        fun getLookup(): GraphServerInterface {
            val registry = LocateRegistry.getRegistry()
            return (registry.lookup(NAME) as GraphServerInterface)
        }

        fun close() {
            LocateRegistry.getRegistry().unbind(NAME)
            UnicastRemoteObject.unexportObject(obj, false)
        }
    }
}