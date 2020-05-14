package thirdparty.Aksenov239.clh;

import connectivity.sequential.general.DynamicConnectivity;
import org.openjdk.jmh.logic.BlackHole;
import thirdparty.Aksenov239.sequential.SequentialDynamicGraph;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by Meepo on 1/3/2018.
 */
public class CLHDynamicGraph implements DynamicConnectivity {
    Random rnd = new Random(239);

    SequentialDynamicGraph sdg;

    int N;
    int T;
    int TRIES;

    public CLHDynamicGraph(int n, int threads) {
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

    public FCCLH fc;
    final private Request[] readRequests;

    public void reinitialize() {
        fc = new FCCLH(T);
        allocatedRequests = new ThreadLocal<>();
    }

    private ThreadLocal<Request> allocatedRequests = new ThreadLocal<>();

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

    public class Request extends FCCLH.CLHRequest {
        volatile int type;
        volatile int u, v;

        volatile int status;

        public Request() {
            super();
            status = PUSHED;
        }

        public void set(int type, int u, int v) {
            this.u = u;
            this.v = v;
            this.type = type;
            status = PUSHED;
        }

        // For result
        volatile boolean result;
    }

    public void sleep() {
        BlackHole.consumeCPU(300);
    }

    AtomicInteger combiners = new AtomicInteger(0);

    public void handleRequest(Request request) {
        if (request.current.backward != request) {
            System.err.println(request.current.backward + " " + request);
            throw new AssertionError();
        }
        if (fc.addRequest(request)) {
            if (combiners.incrementAndGet() > 1) {
                throw new AssertionError();
            }

            FCCLH.CLHRequest[] requests = fc.loadRequests();

//            System.err.println(requests.length);

            int readLength = 0;
            for (int i = 0; i < requests.length; i++) {
                Request r = (Request) requests[i];
                if (r == null) {
                    assert requests[i - 1] == request;
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

            combiners.decrementAndGet();
            fc.release(request, true);
        } else {
            int currentStatus;
            while ((currentStatus = request.status) == PUSHED) {
                sleep();
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

            fc.release(request, false);
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
