package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class NetworkSwitchMachine extends DataMachine implements IOpticalComputationProvider {

    public static final int EUT_PER_HATCH = GTValues.VA[GTValues.IV];
    private int EUt;
    private boolean call;

    public NetworkSwitchMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    protected int calculateEnergyUsage() {
        int receivers = 0;
        int transmitters = 0;
        for (var part : this.getParts()) {
            Block block = part.self().getBlockState().getBlock();
            if (PartAbility.COMPUTATION_DATA_RECEPTION.isApplicable(block)) {
                ++receivers;
            }
            if (PartAbility.COMPUTATION_DATA_TRANSMISSION.isApplicable(block)) {
                ++transmitters;
            }
        }
        return GTValues.VA[GTValues.IV] * (receivers + transmitters);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        int size = 0;
        for (var part : this.getParts()) {
            if (part instanceof IOpticalComputationHatch) {
                size++;
            }
        }
        this.EUt = size * EUT_PER_HATCH;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.EUt = 0;
    }

    @Override
    public int getEnergyUsage() {
        return isFormed() ? EUt : 0;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        // transform into two-state system for display
        MultiblockDisplayText.builder(textList, isFormed()).setWorkingStatus(true, isActive() && isWorkingEnabled()).setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.idling", "gtceu.multiblock.data_bank.providing").addEnergyUsageExactLine(getEnergyUsage()).addComputationUsageLine(this.getMaxCWU()).addWorkingStatusLine();
    }

    @Override
    public long requestCWU(long cwu, boolean simulate) {
        if (call) return 0;
        call = true;
        long result = 0;
        for (IOpticalComputationProvider provider : computationProviderList.providers) {
            if (provider.canBridge()) {
                result += provider.requestCWU(cwu - result, simulate);
                if (result >= cwu) break;
            }
        }
        call = false;
        return result;
    }

    @Override
    public long getMaxCWU() {
        if (call) return 0;
        call = true;
        long result = 0;
        for (IOpticalComputationProvider provider : computationProviderList.providers) {
            if (provider.canBridge()) {
                result += provider.getMaxCWU();
            }
        }
        call = false;
        return result;
    }
}
