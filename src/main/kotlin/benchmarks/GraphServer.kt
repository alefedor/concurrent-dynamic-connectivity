package benchmarks

import benchmarks.util.Graph
import benchmarks.util.downloadOrCreateAndParseGraph
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.server.UnicastRemoteObject

val BORUVKA_USA_ROADS_GRAPH_PARAMS = Triple("BORUVKA-USA-DISTANCE", "gr gz", "http://users.diag.uniroma1.it/challenge9/data/USA-road-d/USA-road-d.W.gr.gz")
val BORUVKA_RANDOM_GRAPH_PARAMS = Triple("BORUVKA-RAND-1M-10M", "rand Erdos-Renyi", "1000000 10000000")
val BORUVKA_WORST_CASE_GRAPH_PARAMS = Triple("BORUVKA-WORST_CASE-4M", "worst_case", "4000000")
val BORUVKA_BERKELEY_STANFORD_WEB_GRAPH_PARAMS = Triple("BORUVKA-BERKELEY_STANFORD_WEB", "txt gz", "http://snap.stanford.edu/data/web-BerkStan.txt.gz")
val BORUVKA_INTERNET_TOPOLOGY_GRAPH_PARAMS = Triple("BORUVKA-INTERNET_TOPOLOGY", "txt gz", "http://snap.stanford.edu/data/as-skitter.txt.gz")

lateinit var BORUVKA_USA_ROADS_GRAPH: Graph
lateinit var BORUVKA_RANDOM_GRAPH: Graph
lateinit var BORUVKA_WORST_CASE_GRAPH: Graph
lateinit var BORUVKA_BERKELEY_STANFORD_WEB_GRAPH: Graph
lateinit var BORUVKA_INTERNET_TOPOLOGY_GRAPH: Graph

enum class GraphParams : Serializable {
    USA_ROADS,
    RANDOM,
    BERKELEY_STANFORD_WEB,
    INTERNET_TOPOLOGY
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
    fun graphByParams(paramsBoruvka: GraphParams): Graph
}

class GraphServer : UnicastRemoteObject(), GraphServerInterface {
    override fun graphByParams(paramsBoruvka: GraphParams) = when (paramsBoruvka) {
        GraphParams.USA_ROADS -> {
            if (!::BORUVKA_USA_ROADS_GRAPH.isInitialized)
                BORUVKA_USA_ROADS_GRAPH = loadGraph(BORUVKA_USA_ROADS_GRAPH_PARAMS)
            BORUVKA_USA_ROADS_GRAPH
        }
        GraphParams.RANDOM -> {
            if (!::BORUVKA_RANDOM_GRAPH.isInitialized)
                BORUVKA_RANDOM_GRAPH = loadGraph(BORUVKA_RANDOM_GRAPH_PARAMS)
            BORUVKA_RANDOM_GRAPH
        }
        GraphParams.BERKELEY_STANFORD_WEB -> {
            if (!::BORUVKA_BERKELEY_STANFORD_WEB_GRAPH.isInitialized)
                BORUVKA_BERKELEY_STANFORD_WEB_GRAPH = loadGraph(BORUVKA_BERKELEY_STANFORD_WEB_GRAPH_PARAMS)
            BORUVKA_BERKELEY_STANFORD_WEB_GRAPH
        }
        GraphParams.INTERNET_TOPOLOGY -> {
            if (!::BORUVKA_INTERNET_TOPOLOGY_GRAPH.isInitialized)
                BORUVKA_INTERNET_TOPOLOGY_GRAPH = loadGraph(BORUVKA_INTERNET_TOPOLOGY_GRAPH_PARAMS)
            BORUVKA_INTERNET_TOPOLOGY_GRAPH
        }
    }
}