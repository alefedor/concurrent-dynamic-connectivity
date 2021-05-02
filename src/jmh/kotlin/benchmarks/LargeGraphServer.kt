package benchmarks

import benchmarks.util.Graph
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

private lateinit var ROAD_GRAPH: Graph
private lateinit var TWITTER_GRAPH: Graph
private lateinit var WEB_GRAPH: Graph
private lateinit var RANDOM_GRAPH: Graph
private lateinit var KRON_GRAPH: Graph

enum class LargeGraph : Serializable {
    KRON,
    RANDOM,
    ROAD,
    TWITTER,
    WEB
}

fun loadGraph(graph: LargeGraph): Graph =
    when (graph) {
        LargeGraph.KRON -> loadGraph("gapbs/benchmark/graphs/kron", "el", "")
        LargeGraph.RANDOM -> loadGraph("gapbs/benchmark/graphs/urand", "el", "")
        LargeGraph.ROAD -> loadGraph("gapbs/benchmark/graphs/road", "el", "")
        LargeGraph.TWITTER -> loadGraph("gapbs/benchmark/graphs/twitter", "el", "")
        LargeGraph.WEB -> loadGraph("gapbs/benchmark/graphs/web", "el", "")
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
        LargeGraph.TWITTER -> {
            if (!::TWITTER_GRAPH.isInitialized)
                TWITTER_GRAPH = loadGraph(params)
            TWITTER_GRAPH
        }
        LargeGraph.WEB -> {
            if (!::WEB_GRAPH.isInitialized)
                WEB_GRAPH = loadGraph(params)
            WEB_GRAPH
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