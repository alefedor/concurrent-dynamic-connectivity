# Concurrent Dynamic Connectivity

This repository provides experiments and algorithms for reproducing the experiments in our paper, *A Scalable Concurrent Dynamic Connectivity*, accepted in SPAA 2021. Our paper presents the first scalable concurrent dynamic connectivity algorithm by making connectivity queries non-blocking, using per-compopent fine-grained locking, and performing modifications that do not change the spanning forest without taking any locks.

## Algorithms
All algorithms are available at   
https://github.com/alefedor/concurrent-dynamic-connectivity/tree/master/src/main/kotlin/connectivity.

Our single-writer concurrent Euler tour trees:     
https://github.com/alefedor/concurrent-dynamic-connectivity/blob/master/src/main/kotlin/connectivity/concurrent/tree/ConcurrentEulerTourTree.kt

Our concurrent dynamic connectivity:    
https://github.com/alefedor/concurrent-dynamic-connectivity/tree/master/src/main/kotlin/connectivity/concurrent/general/major

## Benchmarks

Benchmark jars can be builded using the following command:
```
./gradlew benchmarkJar largeBenchmarkJar
```

This jars can be executed via `java -jar`. The benchmark for large graphs may also need changing maximum java heap size.

All necessary graphs will be downloaded automatically. Internally, we use JMH (Java Microbenchmark Harness) and its output is written to standard output. After the benchmark end, multiple csv-s are generated for each of the scenarios.
