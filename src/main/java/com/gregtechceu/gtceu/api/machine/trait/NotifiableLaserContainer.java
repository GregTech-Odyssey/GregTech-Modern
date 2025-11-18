package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ILaserContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

public class NotifiableLaserContainer extends NotifiableEnergyContainer implements ILaserContainer {

    public NotifiableLaserContainer(MetaMachine machine, long maxCapacity, long maxInputVoltage, long maxInputAmperage,
                                    long maxOutputVoltage, long maxOutputAmperage) {
        super(machine, maxCapacity, maxInputVoltage, maxInputAmperage, maxOutputVoltage, maxOutputAmperage);
    }

    public static NotifiableLaserContainer emitterContainer(MetaMachine machine, long maxCapacity,
                                                            long maxOutputVoltage, long maxOutputAmperage) {
        return new NotifiableLaserContainer(machine, maxCapacity, 0L, 0L, maxOutputVoltage, maxOutputAmperage);
    }

    public static NotifiableLaserContainer receiverContainer(MetaMachine machine, long maxCapacity,
                                                             long maxInputVoltage, long maxInputAmperage) {
        return new NotifiableLaserContainer(machine, maxCapacity, maxInputVoltage, maxInputAmperage, 0L, 0L);
    }

    @Override
    public void serverTick() {
        long stored = getEnergyStored();
        if (stored > 0 && outputSubs != null) {
            long voltage = getOutputVoltage();
            long canOutput = Math.min(stored, getOutputAmperage() * voltage);
            long energyUsed = 0;
            var side = machine.getFrontFacing();
            var oppositeSide = side.getOpposite();
            var energyContainer = GTCapabilityHelper.getLaser(machine.getNeighbor(side), oppositeSide);
            if (energyContainer != null && canOutput >= energyContainer.getInputVoltage() && energyContainer.inputsEnergy(oppositeSide)) {
                energyUsed += energyContainer.acceptEnergyFromNetwork(this, oppositeSide, voltage, canOutput - energyUsed);
            }
            if (energyUsed > 0) {
                setEnergyStored(stored - energyUsed);
                outputSubs.cycle = 0;
            } else if (outputSubs.cycle < 10) {
                outputSubs.cycle++;
            }
        } else {
            ITickSubscription.unsubscribe(outputSubs);
        }
    }
}
