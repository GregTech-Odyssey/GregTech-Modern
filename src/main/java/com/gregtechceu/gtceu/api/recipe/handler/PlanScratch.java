package com.gregtechceu.gtceu.api.recipe.handler;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;

import java.util.Arrays;

/**
 * 配方规划的线程封闭暂存：槽级预留量与输出空槽占用按纪元戳失效（开始新规划只需纪元加一），外加只追加的预留日志。
 * 规划与提交期间只写原始数组，不分配对象；按线程池化并支持有限重入。
 */
public final class PlanScratch {

    public static final byte FLAG_CONSUME = 1;
    public static final byte FLAG_FLUID = 2;
    public static final byte FLAG_OUTPUT = 4;

    private static final ThreadLocal<Pool> POOL = ThreadLocal.withInitial(Pool::new);

    private long[] reserved = new long[64];
    private int[] claim = new int[64];
    private int[] stamp = new int[64];
    private int epoch = 1;

    int[] touched = new int[16];
    int[] itemOffsets = new int[8];
    int[] fluidOffsets = new int[8];
    int[] itemVersions = new int[8];
    int[] fluidVersions = new int[8];
    int slotCount;

    int[] logMember = new int[32];
    int[] logSlot = new int[32];
    int[] logEntry = new int[32];
    long[] logAmount = new long[32];
    byte[] logFlags = new byte[32];
    int logSize;

    long[] outLeft = new long[16];
    long[] itemNeed = new long[16];
    long[] fluidNeed = new long[16];

    private OverlapParallel overlap;

    private PlanScratch() {}

    private static Pool last;

    public static PlanScratch acquire() {
        return pool().acquire();
    }

    public static void release() {
        pool().depth--;
    }

    private static Pool pool() {
        var p = last;
        if (p != null && p.owner == Thread.currentThread()) return p;
        p = POOL.get();
        last = p;
        return p;
    }

    private static final class Pool {

        private final Thread owner = Thread.currentThread();
        private PlanScratch[] scratches = new PlanScratch[4];
        private int depth;

        PlanScratch acquire() {
            int d = depth;
            if (d == scratches.length) scratches = Arrays.copyOf(scratches, d * 2);
            var scratch = scratches[d];
            if (scratch == null) {
                scratch = new PlanScratch();
                scratches[d] = scratch;
            }
            depth = d + 1;
            return scratch;
        }
    }

    void begin(int slots) {
        if (slots > reserved.length) {
            int n = Math.max(slots, reserved.length * 2);
            reserved = new long[n];
            claim = new int[n];
            stamp = new int[n];
            epoch = 1;
            Arrays.fill(touched, 0);
        } else if (++epoch == Integer.MAX_VALUE) {
            Arrays.fill(stamp, 0);
            Arrays.fill(touched, 0);
            epoch = 1;
        }
        slotCount = slots;
        logSize = 0;
    }

    int epoch() {
        return epoch;
    }

    long reserved(int index) {
        return stamp[index] == epoch ? reserved[index] : 0;
    }

    int claim(int index) {
        return stamp[index] == epoch ? claim[index] : 0;
    }

    void addReserved(int index, long amount) {
        if (stamp[index] != epoch) {
            stamp[index] = epoch;
            reserved[index] = amount;
            claim[index] = 0;
        } else {
            reserved[index] += amount;
        }
    }

    void setClaim(int index, int value) {
        if (stamp[index] != epoch) {
            stamp[index] = epoch;
            reserved[index] = 0;
        }
        claim[index] = value;
    }

    void ensureMembers(int items, int fluids) {
        if (items > itemOffsets.length) {
            itemOffsets = new int[items * 2];
            itemVersions = new int[items * 2];
        }
        if (fluids > fluidOffsets.length) {
            fluidOffsets = new int[fluids * 2];
            fluidVersions = new int[fluids * 2];
        }
        if (items + fluids > touched.length) touched = new int[(items + fluids) * 2];
    }

    long[] need(boolean fluid, int size) {
        if (fluid) {
            if (fluidNeed.length < size) fluidNeed = new long[Math.max(size, fluidNeed.length * 2)];
            return fluidNeed;
        }
        if (itemNeed.length < size) itemNeed = new long[Math.max(size, itemNeed.length * 2)];
        return itemNeed;
    }

    long[] outLeft(int size) {
        if (outLeft.length < size) outLeft = new long[Math.max(size, outLeft.length * 2)];
        return outLeft;
    }

    OverlapParallel overlap() {
        var o = overlap;
        if (o == null) overlap = o = new OverlapParallel();
        return o;
    }

    void log(int member, int slot, int entry, long amount, byte flags) {
        int n = logSize;
        if (n == logMember.length) {
            int c = n * 2;
            logMember = Arrays.copyOf(logMember, c);
            logSlot = Arrays.copyOf(logSlot, c);
            logEntry = Arrays.copyOf(logEntry, c);
            logAmount = Arrays.copyOf(logAmount, c);
            logFlags = Arrays.copyOf(logFlags, c);
        }
        logMember[n] = member;
        logSlot[n] = slot;
        logEntry[n] = entry;
        logAmount[n] = amount;
        logFlags[n] = flags;
        logSize = n + 1;
    }

    public void logCustom(int member, int token, int entry, long amount, AEKeyType type, boolean consume, boolean output) {
        byte flags = (byte) ((consume ? FLAG_CONSUME : 0) | (type == AEKeyTypes.FLUIDS ? FLAG_FLUID : 0) | (output ? FLAG_OUTPUT : 0));
        log(member, token, entry, amount, flags);
    }

    public int logSize() {
        return logSize;
    }

    public int logMember(int i) {
        return logMember[i];
    }

    public int logToken(int i) {
        return logSlot[i];
    }

    public int logEntry(int i) {
        return logEntry[i];
    }

    public long logAmount(int i) {
        return logAmount[i];
    }

    public boolean logConsumes(int i) {
        return (logFlags[i] & FLAG_CONSUME) != 0;
    }

    public boolean logIsFluid(int i) {
        return (logFlags[i] & FLAG_FLUID) != 0;
    }

    public long reservedOn(int member, int token) {
        long r = 0;
        for (int i = 0; i < logSize; i++) {
            if (logMember[i] == member && logSlot[i] == token) r += logAmount[i];
        }
        return r;
    }
}
