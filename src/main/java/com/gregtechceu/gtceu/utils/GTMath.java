package com.gregtechceu.gtceu.utils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GTMath {

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static List<ItemStack> splitStacks(ItemStack stack, long amount) {
        int count = saturatedCast(amount);
        int fullStacks = count / 64;
        int rem = count % 64;
        List<ItemStack> stacks = new ArrayList<>(fullStacks + 1);
        if (fullStacks > 0) stacks.addAll(Collections.nCopies(fullStacks, stack.copyWithCount(64)));
        if (rem > 0) stacks.add(stack.copyWithCount(rem));
        return stacks;
    }

    public static int[] split(long value) {
        IntArrayList result = new IntArrayList();
        while (value > 0) {
            int intValue = (int) Math.min(value, Integer.MAX_VALUE);
            result.add(intValue);
            value -= intValue;
        }
        return result.toIntArray();
    }

    public static long mulDivFloor(long a, long b, long c) {
        long hi = Math.multiplyHigh(a, b);
        long lo = a * b;
        if (hi == 0 && lo >= 0) return lo / c;
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(c)).longValue();
    }

    public static long mulDivCeil(long a, long b, long c) {
        long hi = Math.multiplyHigh(a, b);
        long lo = a * b;
        if (hi == 0 && lo >= 0) return lo / c + (lo % c == 0 ? 0 : 1);
        var r = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).add(BigInteger.valueOf(c - 1)).divide(BigInteger.valueOf(c));
        return r.bitLength() < 64 ? r.longValue() : Long.MAX_VALUE;
    }

    public static int saturatedCast(long value) {
        if (value > 2147483647L) {
            return Integer.MAX_VALUE;
        } else {
            return value < -2147483648L ? Integer.MIN_VALUE : (int) value;
        }
    }

    public static int hashInts(int... vals) {
        return Arrays.hashCode(vals);
    }

    public static int hashLongs(long... vals) {
        return Arrays.hashCode(vals);
    }

    public static float ratio(BigInteger a, BigInteger b) {
        return new BigDecimal(a).divide(new BigDecimal(b), MathContext.DECIMAL32).floatValue();
    }
}
