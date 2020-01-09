package connectivity.sequential

class SlowConnectivity(private val size: Int) {
    val graph = Array(size) { mutableListOf<Int>() }

    fun addEdge(u: Int, v: Int) {
        graph[u].add(v)
        graph[v].add(u)
    }

    fun removeEdge(u: Int, v: Int) {
        graph[u].removeIf { it == v }
        graph[v].removeIf { it == u }
    }

    fun sameComponent(u: Int, v: Int) = dfs(v, u, BooleanArray(size))

    private fun dfs(v: Int, need: Int, used: BooleanArray): Boolean {
        used[v] = true
        if (v == need) return true
        for (x in graph[v])
            if (!used[x] && dfs(x, need, used))
                return true
        return false
    }
}