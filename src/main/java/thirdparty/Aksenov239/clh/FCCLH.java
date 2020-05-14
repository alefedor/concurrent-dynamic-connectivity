package thirdparty.Aksenov239.clh;

import org.openjdk.jmh.logic.BlackHole;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Created by Meepo on 1/3/2018.
 */
public class FCCLH {
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

    public static class CLHNode {
        volatile int locked;

        volatile CLHRequest backward;
    }

    public static abstract class CLHRequest {
        volatile CLHNode current;
        volatile CLHNode prev;

        public CLHRequest() {
            current = new CLHNode();
            current.backward = this;
        }
    }

    static final AtomicReferenceFieldUpdater<FCCLH, CLHNode> lastUpdater =
            AtomicReferenceFieldUpdater.newUpdater(FCCLH.class, CLHNode.class, "last");
    private volatile CLHNode last;

    private CLHRequest[] requests;

    public FCCLH(int T) {
        last = new CLHNode();
        requests = new CLHRequest[T + 1];
    }

    public boolean addRequest(CLHRequest request) { // whether combiner or not
        request.current.locked = 1;
//        request.current.backward = request;

        assert request.current.backward == request;
        assert request.prev == null;

        CLHNode now = lastUpdater.getAndSet(this, request.current);

        request.prev = now;

        assert now != null;
        assert request.current.backward == request;

        while (now.locked == 1) {
            BlackHole.consumeCPU(20);
        }

        return now.locked == 0; // 0 if combiner, 2 if not
    }

    public CLHRequest[] loadRequests() {
        CLHNode last = this.last;
        assert last.locked != 0;
//        System.err.println("LR " + last);
        int j = 0;
        while (true) {
            requests[j] = last.backward;
            assert requests[j].current.locked != 0;
            if (requests[j] == null) {
                throw new AssertionError();
            }
            while (requests[j].prev == null) {
                BlackHole.consumeCPU(20);
            }
            last = requests[j].prev;
            j++;
            if (last.locked == 0) {
                break;
            }
            last.locked = 2;
        }
        requests[j] = null;

        // swap prev's of combiner and the last
        if (j > 1) {
            CLHNode tmp = requests[0].current;
            requests[0].current = requests[j - 1].current;
            requests[j - 1].current = tmp;

            tmp = requests[0].prev;
            requests[0].prev = requests[j - 1].prev;
            requests[j - 1].prev = tmp;
        }

        return requests;
    }

    public void release(CLHRequest request, boolean combiner) {
        CLHNode current = request.current;

        CLHNode prev = request.prev;
        prev.backward = request;
        request.prev = null;
        request.current = prev;

        assert current.locked != 0;

        if (combiner) {
            current.backward = null;
            current.locked = 0;
        }

        assert request.current.backward == request;
    }
}
