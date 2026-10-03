package com.gregtechceu.gtceu.common.machine.multiblock.primitive;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fluids.FluidType;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PrimitiveWorkableMachine extends WorkableMultiblockMachine {

    @SaveToDisk
    public final NotifiableInventory<AEItemKey> importItems;
    @SaveToDisk
    public final NotifiableInventory<AEItemKey> exportItems;
    @SaveToDisk
    public final NotifiableInventory<AEFluidKey> importFluids;
    @SaveToDisk
    public final NotifiableInventory<AEFluidKey> exportFluids;

    public PrimitiveWorkableMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.importItems = createImportItemHandler(args);
        this.exportItems = createExportItemHandler(args);
        this.importFluids = createImportFluidHandler(args);
        this.exportFluids = createExportFluidHandler(args);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected NotifiableInventory<AEItemKey> createImportItemHandler(Object... args) {
        return NotifiableInventory.items(this, getRecipeType().getMaxInputs(ItemRecipeInfo.INSTANCE), IO.IN);
    }

    protected NotifiableInventory<AEItemKey> createExportItemHandler(Object... args) {
        return NotifiableInventory.items(this, getRecipeType().getMaxOutputs(ItemRecipeInfo.INSTANCE), IO.OUT);
    }

    protected NotifiableInventory<AEFluidKey> createImportFluidHandler(Object... args) {
        return NotifiableInventory.fluids(this, getRecipeType().getMaxInputs(FluidRecipeInfo.INSTANCE),
                32 * FluidType.BUCKET_VOLUME, IO.IN);
    }

    protected NotifiableInventory<AEFluidKey> createExportFluidHandler(Object... args) {
        return NotifiableInventory.fluids(this, getRecipeType().getMaxOutputs(FluidRecipeInfo.INSTANCE),
                32 * FluidType.BUCKET_VOLUME, IO.OUT);
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(importItems.storage);
        clearInventory(exportItems.storage);
    }
}
