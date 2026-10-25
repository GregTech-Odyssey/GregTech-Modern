package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTUtil;

/**
 * 导线传输模型：电压不变，每安每格扣 lossPerBlock EU，电流逐格减少；超过导线额定电压时截到额定并发热。
 * 每个源安的到货能量 = min(路径最低额定电压, 源电压 − 累计线损)，与按包传的上游能量结果一致。
 */
public final class CableTransfer {

    public static final long AMP_UNIT = 10_000;
    public static final int OVERAMP_HEAT_PER_AMP = 40;

    private CableTransfer() {}

    public static long pathVoltage(long voltage, EnergyRoutePath path) {
        return Math.min(voltage, path.getMinVoltage());
    }

    public static long energyPerAmp(long voltage, EnergyRoutePath path) {
        return Math.min(path.getMinVoltage(), voltage - path.getMaxLoss());
    }

    public static long offer(long available, long voltage, long perAmp) {
        return perAmp == voltage ? available : GTMath.mulDivFloor(available, perAmp, voltage);
    }

    public static long cost(long accepted, long voltage, long perAmp) {
        return perAmp == voltage ? accepted : GTMath.mulDivCeil(accepted, voltage, perAmp);
    }

    public static void load(CableBlockEntity[] cables, long voltage, long paid) {
        long sourceUnits = GTMath.mulDivFloor(paid, AMP_UNIT, voltage);
        long loss = 0;
        long minVoltage = Long.MAX_VALUE;
        for (var c : cables) {
            var data = c.getNodeData();
            loss += data.getLossPerBlock();
            long rated = data.getVoltage();
            if (rated < minVoltage) minVoltage = rated;
            long here = Math.min(voltage, minVoltage);
            long perAmp = Math.min(minVoltage, voltage - loss);
            if (perAmp <= 0) return;
            c.carry(here, perAmp == here ? sourceUnits : GTMath.mulDivFloor(sourceUnits, perAmp, here), voltage);
        }
    }

    public static int overvoltageHeat(long voltage, long rated) {
        return (int) (Math.log(Math.max(1, GTUtil.getTierByVoltage(voltage) - GTUtil.getTierByVoltage(rated))) * 45 + 36.5);
    }
}
