package thirdparty.Aksenov239;

public class BlackHole {
    private static long consumedCPU = 65;

    public static void consumeCPU(long tokens) {
        long t = consumedCPU;

        for (long i = 0; i < tokens; i++) {
            t += (t * 0x5DEECE66DL + 0xBL) & (0xFFFFFFFFFFFFL);
        }

        if (t == 42) {
            consumedCPU += t;
        }
    }
}
