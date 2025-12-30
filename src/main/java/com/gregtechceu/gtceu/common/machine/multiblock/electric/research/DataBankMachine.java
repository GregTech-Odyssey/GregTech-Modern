package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class DataBankMachine extends DataMachine {

    public final List<IDataAccessHatch> dataAccesses = new ArrayList<>();
    public final List<IDataAccessHatch> receptions = new ArrayList<>();
    public final List<IDataAccessHatch> transmissions = new ArrayList<>();

    public DataBankMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        dataAccesses.clear();
        receptions.clear();
        transmissions.clear();
        for (IMultiPart part : this.getParts()) {
            Block block = part.self().getBlockState().getBlock();
            if (part instanceof IDataAccessHatch hatch && PartAbility.DATA_ACCESS.isApplicable(block)) {
                dataAccesses.add(hatch);
            } else if (part instanceof IDataAccessHatch hatch && PartAbility.OPTICAL_DATA_RECEPTION.isApplicable(block)) {
                receptions.add(hatch);
            } else if (part instanceof IDataAccessHatch hatch && PartAbility.OPTICAL_DATA_TRANSMISSION.isApplicable(block)) {
                transmissions.add(hatch);
            }
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        dataAccesses.clear();
        receptions.clear();
        transmissions.clear();
    }
}
