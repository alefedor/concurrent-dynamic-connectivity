package thirdparty.Aksenov239.fc;

import connectivity.sequential.general.DynamicConnectivity;
import connectivity.sequential.general.SequentialDynamicConnectivity;
import org.openjdk.jmh.logic.BlackHole;
import sun.misc.Unsafe;
import thirdparty.Aksenov239.sequential.SequentialDynamicGraph;

import java.lang.reflect.Constructor;

/**
 * User: Aksenov Vitaly
 * Date: 14.07.2017
 * Time: 15:56
 */
public class FCClassicDynamicGraphFlush implements DynamicConnectivity {
    SequentialDynamicConnectivity sdg;

    int N;
    int T;
    int TRIES;

    public FCClassicDynamicGraphFlush(int n, int threads) {
        T = threads;
        TRIES = T;
        N = n;

        sdg = new SequentialDynamicConnectivity(n);

        readRequests = new Request[T];
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
    private final Request[] readRequests;

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
        int type;
        int u, v;

        int status;

        boolean leader;

        public Request() {
            status = PUSHED;
        }

        public boolean holdsRequest() {
            return status != FINISHED;
        }

        public void set(int type, int u, int v) {
            this.type = type;
            this.u = u;
            this.v = v;
            status = PUSHED;
            unsafe.storeFence();
        }

        // For result
        boolean result;
    }

    public void sleep() {
        BlackHole.consumeCPU(300);
    }

    public boolean leaderExists;
    public FCArray.FCRequest[] loadedRequests;

    public void handleRequest(Request request) {
        fc.addRequest(request);
        while (true) {
            unsafe.loadFence();
            boolean isLeader = request.leader;
            int currentStatus = request.status;

            if (!(isLeader || currentStatus != FINISHED)) { // request.leader || request.holdsRequest()
                break;
            }

            if (!leaderExists) {
                if (fc.tryLock()) {
                    leaderExists = true;
                    isLeader = request.leader = true;
                    unsafe.storeFence();
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

                        unsafe.storeFence();
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
                            isConnected(request);
                        } else {
                            if (r.type == ADD) { // the type could be add or remove
                                addEdge(r);
                            } else {
                                removeEdge(r);
                            }
                            r.status = FINISHED;
                        }
                    }
                    
                    unsafe.storeFence();

                    fc.cleanup();
                }

                leaderExists = false;
                request.leader = false;
                fc.unlock();
            } else {
                unsafe.loadFence();
                while ((currentStatus = request.status) == PUSHED &&
                        !request.leader && leaderExists) {
                    sleep();
                    unsafe.loadFence();
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

                unsafe.loadFence();
                while (request.status != FINISHED) { // Wait for the combiner to finish
                    sleep();
                    unsafe.loadFence();
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

    public static final Unsafe unsafe;

    static {
        try {
            Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
            unsafeConstructor.setAccessible(true);
            unsafe = unsafeConstructor.newInstance();
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}