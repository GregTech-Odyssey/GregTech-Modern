package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHostTrait;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import appeng.capabilities.Capabilities;
import com.gto.datasynclib.annotations.SaveToDisk;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class HullMachine extends TieredPartMachine {

    private final GridNodeHostTrait gridNodeHost;
    @SaveToDisk
    protected NotifiableEnergyContainer energyContainer;

    public HullMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.gridNodeHost = new GridNodeHostTrait(this);
        reinitializeEnergyContainer();
    }

    protected void reinitializeEnergyContainer() {
        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer = new NotifiableEnergyContainer(this, tierVoltage * 16L, tierVoltage, 1L, tierVoltage, 1L);
    }

    @Override
    public @Nullable <T> Object getGTCapability(Class<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.ENERGY_CONTAINER) {
            if (side == null || side != getFrontFacing()) return energyContainer;
            return GTCapability.EMPTY;
        }
        return super.getGTCapability(cap, side);
    }

    @Override
    public @Nullable <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == Capabilities.IN_WORLD_GRID_NODE_HOST) {
            return Capabilities.IN_WORLD_GRID_NODE_HOST.orEmpty(cap, LazyOptional.of(() -> gridNodeHost));
        }
        return null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel level) {
            TaskHandler.enqueueTask(level, gridNodeHost::init, 0);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        gridNodeHost.getMainNode().destroy();
    }

    @Override
    public void setFrontFacing(Direction facing) {
        super.setFrontFacing(facing);
        if (isFacingValid(facing)) {
            gridNodeHost.init();
        }
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        CompoundTag nbt = new CompoundTag();
        gridNodeHost.getMainNode().saveToNBT(nbt);
        tag.put("grid_node", nbt);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        gridNodeHost.getMainNode().loadFromNBT(tag.getCompound("grid_node"));
    }

    //////////////////////////////////////
    // ********** Misc **********//
    //////////////////////////////////////

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }
}
