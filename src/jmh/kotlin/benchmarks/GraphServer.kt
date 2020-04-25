package benchmarks

import benchmarks.util.Graph
import benchmarks.util.downloadOrCreateAndParseGraph
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

val USA_ROADS_GRAPH_PARAMS = Triple("USA-ROADS", "gr gz", "http://users.diag.uniroma1.it/challenge9/data/USA-road-d/USA-road-d.W.gr.gz")
val RANDOM_GRAPH_N_PARAMS = Triple("RANDOM-N", "rand", "2000000 4000000")
val RANDOM_GRAPH_NLOG_PARAMS = Triple("RANDOM-NLOG", "rand", "500000 13000000")
val RANDOM_GRAPH_NSQRT_PARAMS = Triple("RANDOM-NSQRT", "rand", "80000 16000000")
val BERKELEY_STANFORD_WEB_GRAPH_PARAMS = Triple("BERKELEY-STANFORD-WEB", "txt gz", "http://snap.stanford.edu/data/web-BerkStan.txt.gz")
val INTERNET_TOPOLOGY_GRAPH_PARAMS = Triple("INTERNET-TOPOLOGY", "txt gz", "http://snap.stanford.edu/data/as-skitter.txt.gz")
val RANDOM_DIVIDED_GRAPH_PARAMS = Triple("RANDOM-DIVIDED", "rand_divided", "10 10000 200000")

lateinit var USA_ROADS_GRAPH: Graph
lateinit var RANDOM_GRAPH_N: Graph
lateinit var RANDOM_GRAPH_NLOG: Graph
lateinit var RANDOM_GRAPH_NSQRT: Graph
lateinit var BERKELEY_STANFORD_WEB_GRAPH: Graph
lateinit var INTERNET_TOPOLOGY_GRAPH: Graph
lateinit var RANDOM_DIVIDED_GRAPH: Graph


enum class GraphParams : Serializable {
//    USA_ROADS,
    //RANDOM_N,
//    RANDOM_NLOG,
//    RANDOM_NSQRT,
//    BERKELEY_STANFORD_WEB,
//    INTERNET_TOPOLOGY,
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
    fun graphByParams(paramsBoruvka: GraphParams): Graph
}

class GraphServer : UnicastRemoteObject(), GraphServerInterface {
    override fun graphByParams(paramsBoruvka: GraphParams) = when (paramsBoruvka) {
        /*GraphParams.USA_ROADS -> {
            if (!::USA_ROADS_GRAPH.isInitialized)
                USA_ROADS_GRAPH =
                    loadGraph(USA_ROADS_GRAPH_PARAMS)
            USA_ROADS_GRAPH
        }*/
        /*GraphParams.RANDOM_N -> {
            if (!::RANDOM_GRAPH_N.isInitialized)
                RANDOM_GRAPH_N =
                    loadGraph(RANDOM_GRAPH_N_PARAMS)
            RANDOM_GRAPH_N
        }*/
        /*GraphParams.RANDOM_NLOG -> {
            if (!::RANDOM_GRAPH_NLOG.isInitialized)
                RANDOM_GRAPH_NLOG =
                    loadGraph(RANDOM_GRAPH_NLOG_PARAMS)
            RANDOM_GRAPH_NLOG
        }*/
        /*GraphParams.RANDOM_NSQRT -> {
            if (!::RANDOM_GRAPH_NSQRT.isInitialized)
                RANDOM_GRAPH_NSQRT =
                    loadGraph(RANDOM_GRAPH_NSQRT_PARAMS)
            RANDOM_GRAPH_NSQRT
        }
        GraphParams.BERKELEY_STANFORD_WEB -> {
            if (!::BERKELEY_STANFORD_WEB_GRAPH.isInitialized)
                BERKELEY_STANFORD_WEB_GRAPH =
                    loadGraph(BERKELEY_STANFORD_WEB_GRAPH_PARAMS)
            BERKELEY_STANFORD_WEB_GRAPH
        }*/
        /*GraphParams.INTERNET_TOPOLOGY -> {
            if (!::INTERNET_TOPOLOGY_GRAPH.isInitialized)
                INTERNET_TOPOLOGY_GRAPH =
                    loadGraph(INTERNET_TOPOLOGY_GRAPH_PARAMS)
            INTERNET_TOPOLOGY_GRAPH
        }*/
        GraphParams.RANDOM_DIVIDED -> {
            if (!::RANDOM_DIVIDED_GRAPH.isInitialized)
                RANDOM_DIVIDED_GRAPH = loadGraph(RANDOM_DIVIDED_GRAPH_PARAMS)
            RANDOM_DIVIDED_GRAPH
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val registry = LocateRegistry.createRegistry(1099)
            registry.rebind("//localhost/MyServer", GraphServer())
        }

        fun getLookup(): GraphServerInterface {
            val registry = LocateRegistry.getRegistry()
            return (registry.lookup("//localhost/MyServer") as GraphServerInterface)
        }
    }
}