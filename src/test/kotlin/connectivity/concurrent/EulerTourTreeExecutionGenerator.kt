package connectivity.concurrent

import org.jetbrains.kotlinx.lincheck.Actor
import org.jetbrains.kotlinx.lincheck.CTestConfiguration
import org.jetbrains.kotlinx.lincheck.CTestStructure
import org.jetbrains.kotlinx.lincheck.CTestStructure.OperationGroup
import org.jetbrains.kotlinx.lincheck.execution.ActorGenerator
import org.jetbrains.kotlinx.lincheck.execution.ExecutionGenerator
import org.jetbrains.kotlinx.lincheck.execution.ExecutionScenario
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors


class EulerTourTreeExecutionGenerator(testConfiguration: CTestConfiguration?, testStructure: CTestStructure?) :
    ExecutionGenerator(testConfiguration, testStructure) {
    private val random: Random = Random(0)
    override fun nextExecution(): ExecutionScenario { // Create init execution part
        val validActorGeneratorsForInit =
            testStructure.actorGenerators.stream()
                .filter { ag: ActorGenerator -> !ag.useOnce() && !ag.isSuspendable }
                .collect(Collectors.toList())
        val initExecution: MutableList<Actor> = ArrayList()
        run {
            var i = 0
            while (i < testConfiguration.actorsBefore && !validActorGeneratorsForInit.isEmpty()) {
                val ag =
                    validActorGeneratorsForInit[random.nextInt(validActorGeneratorsForInit.size)]
                initExecution.add(ag.generate())
                i++
            }
        }
        // Create parallel execution part
        // Construct non-parallel groups and parallel one
        val nonParallelGroups = testStructure.operationGroups.stream()
            .filter { g: OperationGroup -> g.nonParallel }
            .collect(Collectors.toList())
        Collections.shuffle(nonParallelGroups)
        val parallelGroup: MutableList<ActorGenerator> =
            ArrayList(testStructure.actorGenerators)
        nonParallelGroups.forEach(Consumer { g: OperationGroup? ->
            parallelGroup.removeAll(
                g!!.actors
            )
        })
        var parallelExecution: MutableList<MutableList<Actor>> =
            ArrayList()
        val threadGens: MutableList<ThreadGen> = ArrayList()
        for (i in 0 until testConfiguration.threads) {
            parallelExecution.add(ArrayList())
            threadGens.add(ThreadGen(i, testConfiguration.actorsPerThread))
        }
        for (i in nonParallelGroups.indices) {
            threadGens[i % threadGens.size].nonParallelActorGenerators
                .addAll(nonParallelGroups[i]!!.actors)
        }
        val tgs2: List<ThreadGen> = ArrayList(threadGens)
        while (!threadGens.isEmpty()) {
            val it = threadGens.iterator()
            while (it.hasNext()) {
                val threadGen = it.next()
                val aGenIndexBound = threadGen.nonParallelActorGenerators.size + parallelGroup.size
                if (aGenIndexBound == 0) {
                    it.remove()
                    continue
                }
                val aGenIndex: Int = random.nextInt(aGenIndexBound)
                var agen: ActorGenerator
                agen = if (aGenIndex < threadGen.nonParallelActorGenerators.size) {
                    getActorGenFromGroup(threadGen.nonParallelActorGenerators, aGenIndex)
                } else {
                    getActorGenFromGroup(
                        parallelGroup,
                        aGenIndex - threadGen.nonParallelActorGenerators.size
                    )
                }
                parallelExecution[threadGen.threadNumber].add(agen.generate())
                if (--threadGen.left == 0) it.remove()
            }
        }
        parallelExecution = parallelExecution.stream()
            .filter { actors: List<Actor?> -> !actors.isEmpty() }
            .collect(Collectors.toList())
        // Create post execution part if the parallel part does not have suspendable actors
        val postExecution: List<Actor>
        if (parallelExecution.stream().noneMatch { actors: List<Actor> ->
                actors.any(Actor::isSuspendable)
            }) {
            postExecution = ArrayList()
            val leftActorGenerators: MutableList<ActorGenerator> =
                ArrayList(parallelGroup)
            for (threadGen in tgs2) leftActorGenerators.addAll(threadGen.nonParallelActorGenerators)
            var i = 0
            while (i < testConfiguration.actorsAfter && !leftActorGenerators.isEmpty()) {
                val agen =
                    getActorGenFromGroup(leftActorGenerators, random.nextInt(leftActorGenerators.size))
                postExecution.add(agen.generate())
                i++
            }
        } else {
            postExecution = emptyList()
        }
        return ExecutionScenario(initExecution, parallelExecution, postExecution)
    }

    private fun getActorGenFromGroup(aGens: MutableList<ActorGenerator>, index: Int): ActorGenerator {
        val aGen = aGens[index]
        if (aGen.useOnce()) aGens.removeAt(index)
        return aGen
    }

    private inner class ThreadGen internal constructor(var threadNumber: Int, var left: Int) {
        val nonParallelActorGenerators: MutableList<ActorGenerator> =
            ArrayList()

    }
}