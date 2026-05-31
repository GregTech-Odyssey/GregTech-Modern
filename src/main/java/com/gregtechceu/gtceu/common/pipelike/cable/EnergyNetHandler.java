package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;

import net.minecraft.core.Direction;

import lombok.Getter;

import java.util.Objects;

public class EnergyNetHandler implements IEnergyContainer {

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
        if (transfer) return 0;
        if (side == null) {
            if (facing == null) return 0;
            side = facing;
        }
        energyToAdd = Math.min(energyToAdd, getEnergyCapacity());
        long energyUsed = 0;
        var pos = cable.getPipePos();
        for (EnergyRoutePath path : net.getNetData(cable.getPipePosLong(), pos)) {
            long add = energyToAdd - energyUsed;
            if (add <= 0) break;
            long loss = path.getMaxLoss();
            long energy = add - loss;
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
            if (!dest.inputsEnergy(facing)) continue;
            transfer = true;
            long accept = dest.acceptEnergyFromNetwork(o, facing, voltage - loss, energy);
            transfer = false;
            if (accept == 0) continue;
            energyUsed += accept + loss;
            for (var c : path.getPath()) {
                var v = Math.min(voltage, c.getMaxVoltage());
                c.incrementAmperage(v, dest == o ? 1000000 : 100 * accept / v);
            }
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
