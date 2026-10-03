package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistillationTower;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblockpro.ParamKey;
import com.gregtechceu.gtceu.api.machine.multiblockpro.PortKey;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.MethodsReturnNonnullByDefault;

import lombok.Getter;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class DistillationTowerMachine extends WorkableElectricMultiblockMachine implements IDistillationTower {

    public static final PortKey LAYER_IN = PortKey.IN;
    public static final PortKey LAYER_OUT = PortKey.OUT;
    public static final ParamKey LAYERS = ParamKey.of("gtceu.multiblock.distillation_tower.layers", "gtceu.multiblock.distillation_tower.layers.desc");

    @Getter
    private final List<RecipeHandlerUnit> fluidOutputs = new ArrayList<>();

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
