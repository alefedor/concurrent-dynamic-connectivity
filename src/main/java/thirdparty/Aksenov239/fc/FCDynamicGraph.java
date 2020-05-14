package thirdparty.Aksenov239.fc;


import connectivity.sequential.general.DynamicConnectivity;
import org.openjdk.jmh.logic.BlackHole;
import thirdparty.Aksenov239.sequential.SequentialDynamicGraph;

import java.util.Random;

/**
 * User: Aksenov Vitaly
 * Date: 14.07.2017
 * Time: 15:56
 */
public class FCDynamicGraph implements DynamicConnectivity {
    Random rnd = new Random(239);

    SequentialDynamicGraph sdg;

    int N;
    int T;
    int TRIES;

    public FCDynamicGraph(int n, int threads) {
        T = threads;
        TRIES = T;
        N = n;

        sdg = new SequentialDynamicGraph(n, 1);

        readRequests = new Request[T];
        reinitialize();
    }

    public void clear() {
        sdg.clear();
        reinitialize();
    }

    public void isConnected(Request request) {
        request.result = sdg.connected(request.u, request.v);
        request.status = FINISHED;
    }

    public void addEdge(Request r) {
        sdg.addEdge(r.u, r.v);
    }

    public void removeEdge(Request r) {
        sdg.removeEdge(r.u, r.v);
    }

    public FCArray fc;
    final private Request[] readRequests;

    public void reinitialize() {
        fc = new FCArray(T);
        allocatedRequests = new ThreadLocal<>();
    }

    private ThreadLocal<Request> allocatedRequests = new ThreadLocal<Request>();

    private Request getLocalRequest() {
        Request request = allocatedRequests.get();
        if (request == null) {
            request = new Request();
            allocatedRequests.set(request);
        }
        return request;
    }

    private static final int PUSHED = 0;
    private static final int PARALLEL = 1;
    private static final int FINISHED = 2;

    private static final int CONNECTED = 0;
    private static final int ADD = 1;
    private static final int REMOVE = 2;

    public class Request extends FCArray.FCRequest {
        volatile int type;
        volatile int u;
        volatile int v;

        volatile int status;
        volatile boolean leader;

        public Request() {
            status = PUSHED;
        }

        public boolean holdsRequest() {
            return status != FINISHED;
        }

        public void set(int type, int u, int v) {
            this.u = u;
            this.v = v;
            this.type = type;
            status = PUSHED;
        }

        // For result
        boolean result;
    }

    public void sleep() {
        BlackHole.consumeCPU(300);
    }

    public volatile boolean leaderExists;
    public volatile FCArray.FCRequest[] loadedRequests;

    public void handleRequest(Request request) {
        fc.addRequest(request);
        while (true) {
            boolean isLeader = request.leader;
            int currentStatus = request.status;

            if (!(isLeader || currentStatus != FINISHED)) { // request.leader || request.holdsRequest()
                break;
            }

            if (!leaderExists) {
                if (fc.tryLock()) {
                    leaderExists = true;
                    isLeader = request.leader = true;
                }
            }

            if (isLeader && currentStatus == PUSHED) {
                for (int t = 0; t < TRIES; t++) {
                    FCArray.FCRequest[] requests = loadedRequests == null ? fc.loadRequests() : loadedRequests;

                    if (requests[0] == null) {
                        fc.cleanup();
                        break;
                    }

                    if (request.status == FINISHED) {
                        request.leader = false;

                        loadedRequests = requests;
                        ((Request) requests[0]).leader = true;
//                        System.err.println("Give  to " + requests[0]);
                        return;
                    }
                    loadedRequests = null;

                    int readLength = 0;
                    for (int i = 0; i < requests.length; i++) {
                        Request r = (Request) requests[i];
                        if (r == null) {
                            break;
                        }
                        if (r.type == CONNECTED) {
                            readRequests[readLength++] = r;
                        } else {
                            if (r.type == ADD) { // the type could be add or remove
                                addEdge(r);
                            } else {
                                removeEdge(r);
                            }
                            r.status = FINISHED;
                        }
                    }

                    for (int i = 0; i < readLength; i++) {
                        readRequests[i].status = PARALLEL;
                    }

                    if (request.type == CONNECTED) {
                        isConnected(request);
                    }

                    for (int i = 0; i < readLength; i++) {
                        Request r = readRequests[i];
                        if (r.type != CONNECTED)
                            continue;
                        while (r.status == PARALLEL) {
                            sleep();
                        }
                    }

                    fc.cleanup();
                }

                leaderExists = false;
                request.leader = false;
                fc.unlock();
            } else {
                while ((currentStatus = request.status) == PUSHED &&
                        !request.leader && leaderExists) {
                    sleep();
                }
                if (currentStatus == PUSHED) { // I'm the leader or no leader at all
                    continue;
                }

                if (currentStatus == PARALLEL && request.type != CONNECTED) {
                    throw new AssertionError("Fuck");
                }

                // The status has to be PARALLEL
                if (currentStatus == PARALLEL) {
                    isConnected(request); // Run in parallel
                }

                while (request.status != FINISHED) { // Wait for the combiner to finish
                    sleep();
                }
                return;
            }
        }
    }

    public boolean connected(int u, int v) {
        Request request = getLocalRequest();
        request.set(CONNECTED, u, v);
        handleRequest(request);
        return request.result;
    }

    public void addEdge(int u, int v) {
        Request request = getLocalRequest();
        request.set(ADD, u, v);
        handleRequest(request);
        return;
    }

    public void removeEdge(int u, int v) {
        Request request = getLocalRequest();
        request.set(REMOVE, u, v);
        handleRequest(request);
        return;
    }
}
