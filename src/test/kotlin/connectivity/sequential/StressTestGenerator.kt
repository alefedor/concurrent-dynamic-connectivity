package connectivity.sequential

class StressTestGenerator {


    fun generate(size: Int) {

    }
}

class Operation(val type: OperationType, val args: List<Int>)

enum class OperationType {
    ADD_TREE_EDGE,
    REMOVE_TREE_EDGE,
    SAME_COMPONENTS
}