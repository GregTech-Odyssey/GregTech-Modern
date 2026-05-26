package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IDistillationTower;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fluids.capability.IFluidHandler;

import lombok.Getter;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class DistillationTowerMachine extends WorkableElectricMultiblockMachine implements IDistillationTower {

    @Getter
    private final List<IFluidHandler> fluidOutputs = new ArrayList<>();

    @Getter
    private final int yOffset;

    public DistillationTowerMachine(MetaMachineBlockEntity holder) {
        this(holder, 1);
    }

    /**
     * Construct DT Machine
     * 
     * @param holder  BlockEntity holder
     * @param yOffset The Y difference between the controller and the first fluid output
     */
    public DistillationTowerMachine(MetaMachineBlockEntity holder, int yOffset) {
        super(holder);
        this.yOffset = yOffset;
    }

    @Override
    public Comparator<IMultiPart> getPartSorter() {
        return Comparator.comparingInt(p -> p.self().getPos().getY());
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (addOutputs()) return;
        onStructureInvalid();
    }

    @Override
    public void onStructureInvalid() {
        fluidOutputs.clear();
        super.onStructureInvalid();
    }
}
