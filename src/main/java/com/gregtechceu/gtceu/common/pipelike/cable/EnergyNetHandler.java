package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;

import net.minecraft.core.Direction;

import lombok.Getter;

import java.util.Objects;

public final class EnergyNetHandler implements IEnergyContainer {

    @Getter
    private EnergyNet net;
    private boolean transfer;
    private final CableBlockEntity cable;
    private final Direction facing;

    public EnergyNetHandler(EnergyNet net, CableBlockEntity cable, Direction facing) {
        this.net = Objects.requireNonNull(net);
        this.cable = Objects.requireNonNull(cable);
        this.facing = facing;
    }

    public void updateNetwork(EnergyNet net) {
        this.net = net;
    }

    @Override
    public long getEnergyCanBeInserted() {
        return getEnergyCapacity();
    }

    @Override
    public long acceptEnergyFromNetwork(Object o, Direction side, long voltage, long energyToAdd) {
        if (transfer || !cable.isWorkingEnabled()) return 0;
        if (side == null) {
            if (facing == null) return 0;
            side = facing;
        }
        long energyUsed = 0;
        var pos = cable.getPipePos();
        for (var path : net.getNetData(cable.getPipeLongPos(), pos)) {
            long available = energyToAdd - energyUsed;
            if (available <= 0) break;
            long perAmp = CableTransfer.energyPerAmp(voltage, path);
            if (perAmp <= 0) continue;

            if (side == path.getTargetFacing() && pos.equals(path.getTargetPipePos())) {
                continue;
            }

            var dest = path.getHandler(getNet().getLevel());
            if (dest == null || dest == o) continue;

            var facing = path.getTargetFacing().getOpposite();
            if (!dest.inputsEnergy(facing)) continue;
            long offer = CableTransfer.offer(available, voltage, perAmp);
            if (offer <= 0) continue;
            transfer = true;
            long accept = dest.acceptEnergyFromNetwork(o, facing, CableTransfer.pathVoltage(voltage, path), offer);
            transfer = false;
            if (accept <= 0) continue;
            long paid = Math.min(available, CableTransfer.cost(accept, voltage, perAmp));
            energyUsed += paid;
            CableTransfer.load(path.getPath(), voltage, paid);
        }
        return energyUsed;
    }

    @Override
    public long getInputAmperage() {
        return cable.getNodeData().getAmperage();
    }

    @Override
    public long getInputVoltage() {
        return cable.getNodeData().getVoltage();
    }

    @Override
    public long getEnergyCapacity() {
        return getInputVoltage() * getInputAmperage();
    }

    @Override
    public long changeEnergy(long energyToAdd) {
        throw new UnsupportedOperationException("Do not use changeEnergy() for cables! Use acceptEnergyFromNetwork()");
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return true;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return true;
    }

    @Override
    public long getEnergyStored() {
        return 0;
    }

    @Override
    public boolean isOneProbeHidden() {
        return true;
    }
}
