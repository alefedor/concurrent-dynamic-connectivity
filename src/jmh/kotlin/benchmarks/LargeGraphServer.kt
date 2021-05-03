package benchmarks

import benchmarks.util.Graph
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

private lateinit var ROAD_GRAPH: Graph
private lateinit var LIVE_JOURNAL_GRAPH: Graph
private lateinit var WEB_GRAPH: Graph
private lateinit var RANDOM_GRAPH: Graph
private lateinit var KRON_GRAPH: Graph

enum class LargeGraph : Serializable {
    KRON,
    RANDOM,
    ROAD,
    LIVE_JOURNAL,
}
fun loadGraph(graph: LargeGraph): Graph =
    when (graph) {
        LargeGraph.KRON -> loadGraph("KRON", "graph bz2", "https://www.cc.gatech.edu/dimacs10/archive/data/kronecker/kron_g500-logn21.graph.bz2")
        LargeGraph.RANDOM -> loadGraph("RANDOM-LARGE", "graph bz2", "https://www.cc.gatech.edu/dimacs10/archive/data/er/er-fact1.5-scale22.graph.bz2")
        LargeGraph.ROAD -> loadGraph("USA-ROADS-FULL", "gr gz", "http://www.diag.uniroma1.it/challenge9/data/USA-road-d/USA-road-d.USA.gr.gz")
        LargeGraph.LIVE_JOURNAL -> loadGraph("LIVE-JOURNAL", "txt gz", "http://snap.stanford.edu/data/soc-LiveJournal1.txt.gz")
    }

interface LargeGraphServerInterface : Remote {
    @Throws(RemoteException::class)
    fun graphByParams(params: LargeGraph): Graph
}

class LargeGraphServer : UnicastRemoteObject(), LargeGraphServerInterface {
    override fun graphByParams(params: LargeGraph) = when (params) {
        LargeGraph.KRON -> {
            if (!::KRON_GRAPH.isInitialized)
                KRON_GRAPH = loadGraph(params)
            KRON_GRAPH
        }
        LargeGraph.RANDOM -> {
            if (!::RANDOM_GRAPH.isInitialized)
                RANDOM_GRAPH = loadGraph(params)
            RANDOM_GRAPH
        }
        LargeGraph.ROAD -> {
            if (!::ROAD_GRAPH.isInitialized)
                ROAD_GRAPH = loadGraph(params)
            ROAD_GRAPH
        }
        LargeGraph.LIVE_JOURNAL -> {
            if (!::LIVE_JOURNAL_GRAPH.isInitialized)
                LIVE_JOURNAL_GRAPH = loadGraph(params)
            LIVE_JOURNAL_GRAPH
        }
    }

    companion object {
        var obj: LargeGraphServer? = null
        const val NAME = "//localhost/GraphServer"

        @JvmStatic
        fun main(args: Array<String>) {
            val registry = LocateRegistry.createRegistry(1099)
            obj = LargeGraphServer()
            registry.rebind(NAME, obj)
        }

        fun getLookup(): LargeGraphServerInterface {
            val registry = LocateRegistry.getRegistry()
            return (registry.lookup(NAME) as LargeGraphServerInterface)
        }

        fun close() {
            LocateRegistry.getRegistry().unbind(NAME)
            UnicastRemoteObject.unexportObject(obj, false)
        }
    }
}