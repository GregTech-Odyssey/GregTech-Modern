package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.capability.ElectricItem;
import com.gregtechceu.gtceu.api.item.component.ElectricStats;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GTCapabilityHelper {

    @Nullable
    public static IElectricItem getElectricItem(ItemStack itemStack) {
        var item = itemStack.getItem();
        if (item instanceof IComponentItem componentItem) {
            for (IItemComponent component : componentItem.getComponents()) {
                if (component instanceof ElectricStats electricStats) {
                    return new ElectricItem(itemStack, electricStats.maxCharge, electricStats.tier, electricStats.chargeable, electricStats.dischargeable);
                }
            }
        } else if (item instanceof IGTTool tool && tool.isElectric()) {
            return new ElectricItem(itemStack, 0L, tool.getElectricTier(), true, false);
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("all")
    public static IEnergyStorage getForgeEnergyItem(ItemStack itemStack) {
        return itemStack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
    }

    @Nullable
    public static IFluidHandler getFluidHandler(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(ForgeCapabilities.FLUID_HANDLER, blockEntity, side);
    }

    @Nullable
    public static IItemHandler getItemHandler(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(ForgeCapabilities.ITEM_HANDLER, blockEntity, side);
    }

    @Nullable
    public static IEnergyContainer getEnergyContainer(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, blockEntity, side);
    }

    public static ILaserContainer getLaser(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(GTCapability.CAPABILITY_LASER, blockEntity, side);
    }

    @Nullable
    public static IEnergyInfoProvider getEnergyInfoProvider(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(GTCapability.CAPABILITY_ENERGY_INFO_PROVIDER, blockEntity, side);
    }

    @Nullable
    public static ICoverable getCoverable(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(GTCapability.CAPABILITY_COVERABLE, blockEntity, side);
    }

    @Nullable
    public static IWorkable getWorkable(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(GTCapability.CAPABILITY_WORKABLE, blockEntity, side);
    }

    @Nullable
    public static IControllable getControllable(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityCapability(GTCapability.CAPABILITY_CONTROLLABLE, blockEntity, side);
    }

    @Nullable
    public static RecipeLogic getRecipeLogic(BlockEntity blockEntity) {
        if (MetaMachine.getMachine(blockEntity) instanceof IRecipeLogicMachine recipeLogicMachine) {
            return recipeLogicMachine.getRecipeLogic();
        }
        return null;
    }

    @Nullable
    public static IMaintenanceMachine getMaintenanceMachine(BlockEntity blockEntity) {
        if (MetaMachine.getMachine(blockEntity) instanceof IMaintenanceMachine maintenanceMachine) {
            return maintenanceMachine;
        }
        return null;
    }

    @Nullable
    public static IHazardParticleContainer getHazardContainer(Level level, BlockPos pos, @Nullable Direction side) {
        return getBlockEntityCapability(GTCapability.CAPABILITY_HAZARD_CONTAINER, level.getBlockEntity(pos), side);
    }

    @Nullable
    public static <T> T getBlockEntityCapability(Capability<T> capability, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity != null) {
            return blockEntity.getCapability(capability, side).orElse(null);
        }
        return null;
    }

    @Nullable
    public static IMedicalConditionTracker getMedicalConditionTracker(@NotNull Entity entity) {
        return entity.getCapability(GTCapability.CAPABILITY_MEDICAL_CONDITION_TRACKER, null).orElse(null);
    }
}
