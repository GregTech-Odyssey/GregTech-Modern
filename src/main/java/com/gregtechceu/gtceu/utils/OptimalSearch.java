package com.gregtechceu.gtceu.utils;

import lombok.experimental.UtilityClass;

import java.util.function.LongPredicate;

@UtilityClass
public class OptimalSearch {

    private static final long FAST_PRUNE_THRESHOLD = 1L << 20;

    public static long exactSearch(long lowBound, long startValue, LongPredicate predicate) {
        if (predicate.test(startValue)) return startValue;
        long low = lowBound;
        long high = startValue;
        if (high - low > FAST_PRUNE_THRESHOLD) {
            long current = high;
            while (true) {
                if (predicate.test(current)) {
                    low = current;
                    break;
                }
                high = current;
                current >>= 8;
                if (current <= low) {
                    break;
                }
            }
        }
        while (low + 1 < high) {
            long mid = low + (high - low) / 2;
            if (predicate.test(mid)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public static long exactSearch(long startValue, LongPredicate predicate) {
        return exactSearch(0L, startValue, predicate);
    }

    public static long fuzzySearch(long lowBound, long startValue, LongPredicate predicate, int refineSteps) {
        if (predicate.test(startValue)) return startValue;
        long low = lowBound;
        long high = startValue;
        if (high - low > FAST_PRUNE_THRESHOLD) {
            long current = high;
            while (true) {
                if (predicate.test(current)) {
                    low = current;
                    break;
                }
                high = current;
                current >>= 8;
                if (current <= low) {
                    break;
                }
            }
        }
        for (int i = 0; i < refineSteps && low + 1 < high; i++) {
            long mid = low + (high - low) / 2;
            if (predicate.test(mid)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public static long fuzzySearch(long startValue, LongPredicate predicate) {
        return fuzzySearch(0L, startValue, predicate, 3);
    }
}
