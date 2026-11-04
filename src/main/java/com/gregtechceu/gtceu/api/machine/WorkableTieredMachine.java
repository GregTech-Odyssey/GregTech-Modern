package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IInputLimitableMachine;
import com.gregtechceu.gtceu.api.machine.trait.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.utils.GTUtil;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class WorkableTieredMachine extends TieredEnergyMachine implements IRecipeLogicMachine, IMachineLife, IMufflableMachine, IOverclockMachine, IInputLimitableMachine {

    @Getter
    @SaveToDisk
    @SyncToClient
    public final RecipeLogic recipeLogic;
    @Setter
    @Getter
    @Nullable
    protected GTRecipeType[] availableRecipeTypesCache;
    @Getter
    @SaveToDisk(defaultValue = "0")
    @SyncToClient
    protected int activeRecipeType;
    @Getter
    protected final Int2IntFunction tankScalingFunction;
    @Nullable
    protected ICleanroomProvider cleanroom;
    @SaveToDisk
    public final NotifiableInventory<AEItemKey> importItems;
    @SaveToDisk
    public final NotifiableInventory<AEItemKey> exportItems;
    @SaveToDisk
    public final NotifiableInventory<AEFluidKey> importFluids;
    @SaveToDisk
    public final NotifiableInventory<AEFluidKey> exportFluids;

    @Getter
    protected final RecipeHandlerCache recipeHandlerCache = new RecipeHandlerCache();

    @Getter
    @SaveToDisk(defaultValueGetter = "getMaxOverclockTier")
    protected int overclockTier;

    @Getter
    @Setter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient
    protected boolean isMuffled;

    public WorkableTieredMachine(MetaMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction, Object... args) {
        super(holder, tier, args);
        this.overclockTier = getMaxOverclockTier();
        this.tankScalingFunction = tankScalingFunction;
        this.recipeLogic = createRecipeLogic(args);
        this.importItems = createImportItemHandler(args);
        this.exportItems = createExportItemHandler(args);
        this.importFluids = createImportFluidHandler(args);
        this.exportFluids = createExportFluidHandler(args);
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        long tierVoltage = GTValues.V[getTier()];
        if (isEnergyEmitter()) {
            return NotifiableEnergyContainer.emitterContainer(this, tierVoltage * 64L, tierVoltage, getMaxInputOutputAmperage());
        } else {
            return new NotifiableEnergyContainer(this, tierVoltage * 64L, tierVoltage, 2, 0L, 0L) {

                @Override
                public long getInputAmperage() {
                    if (getEnergyCapacity() / 2 > getEnergyStored() && recipeLogic.isActive()) {
                        return 2;
                    }
                    return 1;
                }
            };
        }
    }

    protected NotifiableInventory<AEItemKey> createImportItemHandler(Object... args) {
        var handler = NotifiableInventory.items(this, getRecipeType().getMaxInputs(ItemRecipeInfo.INSTANCE), IO.IN);
        if (handler.storage.size() == 0) handler.setAvailable(false);
        return handler;
    }

    protected NotifiableInventory<AEItemKey> createExportItemHandler(Object... args) {
        var handler = NotifiableInventory.items(this, getRecipeType().getMaxOutputs(ItemRecipeInfo.INSTANCE), IO.OUT);
        if (handler.storage.size() == 0) handler.setAvailable(false);
        return handler;
    }

    protected NotifiableInventory<AEFluidKey> createImportFluidHandler(Object... args) {
        var handler = NotifiableInventory.fluids(this, getRecipeType().getMaxInputs(FluidRecipeInfo.INSTANCE), this.tankScalingFunction.applyAsInt(this.getTier()), IO.IN);
        if (handler.storage.size() == 0) handler.setAvailable(false);
        return handler;
    }

    protected NotifiableInventory<AEFluidKey> createExportFluidHandler(Object... args) {
        var handler = NotifiableInventory.fluids(this, getRecipeType().getMaxOutputs(FluidRecipeInfo.INSTANCE), this.tankScalingFunction.applyAsInt(this.getTier()), IO.OUT);
        if (handler.storage.size() == 0) handler.setAvailable(false);
        return handler;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // attach self traits
        Map<IO, List<IRecipeHandler>> ioTraits = new EnumMap<>(IO.class);
        for (var trait : getTraits(IRecipeHandlerTrait.class)) {
            if (trait.isAvailable() && trait.getHandlerIO() != IO.NONE) {
                ioTraits.computeIfAbsent(trait.getHandlerIO(), i -> new ArrayList<>()).add(trait);
            }
        }
        recipeHandlerCache.addHandlerLists(this, ioTraits, recipeLogic::updateTickSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        recipeHandlerCache.clear();
    }

    //////////////////////////////////////
    // ********** MISC ***********//
    //////////////////////////////////////
    @Override
    protected long getMaxInputOutputAmperage() {
        return 2L;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(importItems.storage);
        clearInventory(exportItems.storage);
    }

    //////////////////////////////////////
    // ******** OVERCLOCK *********//
    //////////////////////////////////////
    @Override
    public int getMaxOverclockTier() {
        return GTUtil.getTierByVoltage(Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage()));
    }

    @Override
    public int getMinOverclockTier() {
        return 0;
    }

    @Override
    public void setOverclockTier(int tier) {
        if (!isRemote() && tier >= getMinOverclockTier() && tier <= getMaxOverclockTier()) {
            this.overclockTier = tier;
            this.recipeLogic.markLastRecipeDirty();
        }
    }

    @Override
    public long getOverclockVoltage() {
        return Math.min(GTValues.V[overclockTier], Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage()));
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////
    public void setActiveRecipeType(final int activeRecipeType) {
        if (this.activeRecipeType != activeRecipeType) {
            getRecipeLogic().markLastRecipeDirty();
            getRecipeLogic().updateTickSubscription();
            requestSync();
        }
        this.activeRecipeType = activeRecipeType;
    }

    @Nullable
    public ICleanroomProvider getCleanroom() {
        return this.cleanroom;
    }

    public void setCleanroom(@Nullable final ICleanroomProvider cleanroom) {
        if (cleanroom != null && this.cleanroom != null && this.cleanroom.getCleanroomTier() >= cleanroom.getCleanroomTier()) {
            return;
        }
        this.cleanroom = cleanroom;
        getRecipeLogic().markLastRecipeDirty();
        getRecipeLogic().updateTickSubscription();
    }

    @Override
    public boolean hasInputLimitConfig() {
        return IInputLimitableMachine.configurable(importItems.storage, importFluids.storage);
    }

    @Override
    public void setInputLimit(boolean inputLimit) {
        IInputLimitableMachine.limit(inputLimit, importItems.storage, importFluids.storage);
    }

    @Override
    public boolean isInputLimit() {
        return IInputLimitableMachine.limited(importItems.storage, importFluids.storage);
    }
}
