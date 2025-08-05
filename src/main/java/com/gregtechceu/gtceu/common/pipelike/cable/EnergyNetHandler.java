package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;

import java.util.Objects;

public class EnergyNetHandler implements IEnergyContainer {

    private EnergyNet net;
    private boolean transfer;
    private final CableBlockEntity cable;
    private final Direction facing;

    public EnergyNetHandler(EnergyNet net, CableBlockEntity cable, Direction facing) {
        this.net = Objects.requireNonNull(net);
        this.cable = Objects.requireNonNull(cable);
        this.facing = facing;
    }

    public EnergyNet getNet() {
        return net;
    }

    public void updateNetwork(EnergyNet net) {
        this.net = net;
    }

    @Override
    public long getEnergyCanBeInserted() {
        return getEnergyCapacity();
    }

    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage, long energyToAdd) {
        if (transfer) return 0;
        if (side == null) {
            if (facing == null) return 0;
            side = facing;
        }

        long energyUsed = 0;
        var pos = cable.getPipePos();
        for (EnergyRoutePath path : net.getNetData(cable.getPipePosLong(), pos)) {
            long energy = energyToAdd - path.getMaxLoss();
            if (energy <= 0) {
                // Will lose all the energy with this path, so don't use it
                continue;
            }

            if (pos.equals(path.getTargetPipePos()) && side == path.getTargetFacing()) {
                // Do not insert into source handler
                continue;
            }

            IEnergyContainer dest = path.getHandler(getNet().getLevel());
            if (dest == null) continue;

            Direction facing = path.getTargetFacing().getOpposite();
            if (!dest.inputsEnergy(facing) || dest.getEnergyCanBeInserted() <= 0) continue;
            transfer = true;
            long accept = dest.acceptEnergyFromNetwork(facing, voltage - path.getMaxLoss(), energy - energyUsed);
            transfer = false;
            if (accept == 0) continue;
            for (var c : path.getPath()) {
                c.incrementAmperage(voltage, accept / voltage);
                if (voltage > c.getMaxVoltage()) {
                    int heat = (int) (Math.log(GTUtil.getTierByVoltage(voltage) - GTUtil.getTierByVoltage(cable.getMaxVoltage())) * 45 + 36.5);
                    c.applyHeat(heat);
                }
            }
            energyToAdd = energy;
            energyUsed += accept;
            if (energyUsed == energyToAdd) break;
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
