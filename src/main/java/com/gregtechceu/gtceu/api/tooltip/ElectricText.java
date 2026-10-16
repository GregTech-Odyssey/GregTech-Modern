package com.gregtechceu.gtceu.api.tooltip;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 电学读数的唯一格式化入口：电压 V、电流 A、功率 EU/t 或「安 等级」、能量「A·s / A·h 等级」或精确 EU、线损每安每格。
 */
public final class ElectricText {

    private static final double TICKS_PER_SECOND = 20;
    private static final double SECONDS_PER_HOUR = 3600;
    private static final String PREFIXES = " kMGTPEZY";

    private ElectricText() {}

    public static int tierOf(long voltage) {
        return voltage > GTValues.V[GTValues.MAX] ? GTUtil.getFloorTierByVoltage(voltage) : GTUtil.getTierByVoltage(voltage);
    }

    public static int floorTier(long euPerTick) {
        return euPerTick <= 0 ? GTValues.ULV : GTUtil.getFloorTierByVoltage(euPerTick);
    }

    public static Component tier(int tier) {
        return Component.literal(GTValues.VNF[tier]);
    }

    public static MutableComponent voltage(long voltage) {
        return voltage <= 0 ? Component.translatable("gtceu.electric.voltage_value", "0") : voltage(voltage, tierOf(voltage));
    }

    public static MutableComponent voltage(long voltage, int tier) {
        return Component.translatable("gtceu.electric.voltage", FormattingUtil.formatNumbers(voltage), GTValues.VNF[tier]);
    }

    public static MutableComponent amps(double amps) {
        return Component.translatable("gtceu.electric.amps", ampsValue(amps));
    }

    public static MutableComponent power(long euPerTick) {
        return Component.translatable("gtceu.electric.power", FormattingUtil.formatNumbers(euPerTick));
    }

    public static MutableComponent power(double euPerTick) {
        return Component.translatable("gtceu.electric.power", exact(euPerTick));
    }

    public static MutableComponent flow(double amps, int tier) {
        return Component.translatable("gtceu.electric.flow", ampsValue(amps), GTValues.VNF[tier]);
    }

    public static MutableComponent flow(double amps, Component tierName) {
        return Component.translatable("gtceu.electric.flow", ampsValue(amps), tierName);
    }

    public static MutableComponent flow(double amps, String tierName) {
        return Component.translatable("gtceu.electric.flow", ampsValue(amps), tierName);
    }

    public static MutableComponent flowUsage(double used, double capacity, String tierName) {
        return flowUsage(Component.literal(ampsOf(used, capacity)), capacity, tierName);
    }

    public static MutableComponent flowUsage(Component used, double capacity, String tierName) {
        return Component.translatable("gtceu.electric.flow_usage", used, ampsOf(capacity, capacity), tierName);
    }

    public static String ampsOf(double amps, double scale) {
        int exp = scale >= 10000 ? exponent(scale) : 0;
        return exp == 0 ? plainAmps(amps) : withPrefix(amps, exp);
    }

    public static MutableComponent flowOf(long euPerTick, int tier) {
        return flow((double) euPerTick / GTValues.VEX[tier], tier);
    }

    public static MutableComponent flowOf(long euPerTick) {
        return flowOf(euPerTick, floorTier(euPerTick));
    }

    public static MutableComponent energy(long eu, int tier) {
        return energyText(eu, tier, GTValues.VNF[tier]);
    }

    public static MutableComponent energy(double eu, int tier) {
        return energyText(eu, tier, GTValues.VNF[tier]);
    }

    public static MutableComponent energy(double eu, int tier, Component tierName) {
        return energyText(eu, tier, tierName);
    }

    public static MutableComponent energy(BigInteger eu, int tier) {
        return energyText(eu.doubleValue(), tier, GTValues.VNF[tier]);
    }

    private static MutableComponent energyText(double eu, int tier, Object tierName) {
        double seconds = ampSecondsOf(eu, tier);
        if (seconds >= SECONDS_PER_HOUR) {
            return Component.translatable("gtceu.electric.energy", prefixed(seconds / SECONDS_PER_HOUR), tierName);
        }
        return Component.translatable("gtceu.electric.energy_seconds", withPrefix(seconds, 0), tierName);
    }

    public static MutableComponent energyExact(long eu) {
        return Component.translatable("gtceu.electric.energy_exact", FormattingUtil.formatNumbers(eu));
    }

    public static MutableComponent energyExact(BigInteger eu) {
        return Component.translatable("gtceu.electric.energy_exact", FormattingUtil.formatNumbers(eu));
    }

    public static MutableComponent charge(long current, long max, int tier) {
        return charge(current, max, tier, GTValues.VNF[tier]);
    }

    public static MutableComponent charge(double current, double max, int tier, String tierName) {
        double maxSeconds = ampSecondsOf(max, tier);
        double currentSeconds = ampSecondsOf(current, tier);
        if (maxSeconds < SECONDS_PER_HOUR) {
            return Component.translatable("gtceu.electric.charge_seconds", withPrefix(currentSeconds, 0), withPrefix(maxSeconds, 0), tierName);
        }
        int exp = exponent(maxSeconds / SECONDS_PER_HOUR);
        return Component.translatable("gtceu.electric.charge", withPrefix(currentSeconds / SECONDS_PER_HOUR, exp),
                withPrefix(maxSeconds / SECONDS_PER_HOUR, exp), tierName);
    }

    public static MutableComponent energyExact(double eu) {
        return Component.translatable("gtceu.electric.energy_exact", exact(eu));
    }

    public static MutableComponent chargeExact(double current, double max) {
        return Component.translatable("gtceu.electric.charge_exact", exact(current), exact(max));
    }

    public static MutableComponent chargeExact(long current, long max) {
        return Component.translatable("gtceu.electric.charge_exact", FormattingUtil.formatNumbers(current), FormattingUtil.formatNumbers(max));
    }

    public static MutableComponent chargeExact(BigInteger current, BigInteger max) {
        return Component.translatable("gtceu.electric.charge_exact", FormattingUtil.formatNumbers(current), FormattingUtil.formatNumbers(max));
    }

    public static MutableComponent ampSeconds(double amps) {
        return Component.translatable("gtceu.electric.amp_seconds", ampsValue(amps));
    }

    public static MutableComponent ampsPerSecond(double amps) {
        return Component.translatable("gtceu.electric.amps_per_second", ampsValue(amps));
    }

    public static MutableComponent loss(long lossPerBlock, long ratedVoltage) {
        String percent = ratedVoltage <= 0 ? "0" : significant(lossPerBlock * 100D / ratedVoltage);
        return Component.translatable("gtceu.electric.loss", FormattingUtil.formatNumbers(lossPerBlock), percent);
    }

    public static String ampsValue(double amps) {
        return amps >= 10000 ? prefixed(amps) : plainAmps(amps);
    }

    private static String plainAmps(double amps) {
        if (amps == Math.rint(amps)) return FormattingUtil.formatNumbers((long) amps);
        return FormattingUtil.DECIMAL_FORMAT_2F.format(amps);
    }

    private static String exact(double eu) {
        if (!Double.isFinite(eu)) return "0";
        return Math.abs(eu) < Long.MAX_VALUE ? FormattingUtil.formatNumbers(Math.round(eu)) : FormattingUtil.formatNumbers(new BigDecimal(eu).toBigInteger());
    }

    private static double ampSecondsOf(double eu, int tier) {
        return eu / (GTValues.VEX[tier] * TICKS_PER_SECOND);
    }

    private static String prefixed(double value) {
        if (!(value > 0)) return "0";
        return withPrefix(value, exponent(value));
    }

    private static int exponent(double value) {
        if (!(value > 0)) return 0;
        int exp = (int) Math.floor(Math.log10(value) / 3);
        return Math.max(0, Math.min(exp, PREFIXES.length() - 1));
    }

    private static String withPrefix(double value, int exp) {
        if (!(value > 0)) return "0";
        double shown = value / Math.pow(1000, exp);
        return (shown < 0.005 ? "<0.01" : significant(shown)) + prefix(exp);
    }

    private static String prefix(int exp) {
        return exp == 0 ? "" : String.valueOf(PREFIXES.charAt(exp));
    }

    private static String significant(double value) {
        if (value >= 100) return FormattingUtil.formatNumbers(Math.round(value));
        if (value >= 10) return FormattingUtil.DECIMAL_FORMAT_1F.format(value);
        return FormattingUtil.DECIMAL_FORMAT_2F.format(value);
    }
}
