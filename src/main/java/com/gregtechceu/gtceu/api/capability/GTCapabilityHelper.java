package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.compat.EUToFEProvider;
import com.gregtechceu.gtceu.api.capability.forge.GTForgeCapability;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.capability.ElectricItem;
import com.gregtechceu.gtceu.api.item.component.ElectricStats;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyInfoProviderList;
import com.gregtechceu.gtceu.utils.LazyOptionalUtil;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GTCapabilityHelper {

    @Nullable
    public static IMedicalConditionTracker getMedicalConditionTracker(@NotNull Entity entity) {
        return LazyOptionalUtil.get(entity.getCapability(GTForgeCapability.CAPABILITY_MEDICAL_CONDITION_TRACKER, null));
    }

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
        if (blockEntity instanceof GTBlockEntity gtBlockEntity) {
            return gtBlockEntity.getGTCapability(GTCapability.ENERGY_CONTAINER, side);
        } else {
            return EUToFEProvider.getCapability(blockEntity, side);
        }
    }

    @Nullable
    public static IEnergyInfoProvider getEnergyInfoProvider(BlockEntity blockEntity) {
        if (blockEntity instanceof MetaMachineBlockEntity metaMachineBlock) {
            if (metaMachineBlock.metaMachine instanceof IEnergyInfoProvider provider) {
                return provider;
            }
            var list = forceGetCapabilitiesFromTraits(metaMachineBlock.metaMachine.getTraits(), GTCapability.ENERGY_INFO_PROVIDER);
            if (!list.isEmpty()) {
                return list.size() == 1 ? list.getFirst() : new EnergyInfoProviderList(list);
            }
        }
        return null;
    }

    @Nullable
    public static ICoverable getCoverable(BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity instanceof GTBlockEntity gtBlockEntity) {
            return gtBlockEntity.getCoverContainer();
        }
        return null;
    }

    @Nullable
    public static IWorkable getWorkable(BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity instanceof MetaMachineBlockEntity gtBlockEntity) {
            var machine = gtBlockEntity.metaMachine;
            if (machine instanceof IWorkable workable) {
                return workable;
            }
            for (MachineTrait trait : machine.getTraits()) {
                if (trait instanceof IWorkable workable) {
                    return workable;
                }
            }
            return null;
        }
        return null;
    }

    @Nullable
    public static IControllable getControllable(BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity instanceof MetaMachineBlockEntity gtBlockEntity) {
            var machine = gtBlockEntity.metaMachine;
            if (machine instanceof IControllable controllable) {
                return controllable;
            }
            for (MachineTrait trait : machine.getTraits()) {
                if (trait instanceof IControllable controllable) {
                    return controllable;
                }
            }
            return null;
        }
        return null;
    }

    public static ILaserContainer getLaser(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityGTCapability(GTCapability.LASER, blockEntity, side);
    }

    public static IOpticalComputationProvider getComputation(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityGTCapability(GTCapability.COMPUTATION_PROVIDER, blockEntity, side);
    }

    public static IDataAccessHatch getDataAccess(BlockEntity blockEntity, @Nullable Direction side) {
        return getBlockEntityGTCapability(GTCapability.DATA_ACCESS, blockEntity, side);
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
    public static <T> T getBlockEntityCapability(Capability<T> capability, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity != null) {
            return blockEntity.getCapability(capability, side).orElse(null);
        }
        return null;
    }

    @Nullable
    public static <T> T getBlockEntityGTCapability(Class<T> capability, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity instanceof GTBlockEntity gtBlockEntity) {
            return gtBlockEntity.getGTCapability(capability, side);
        }
        return null;
    }

    public static <T> List<T> forceGetCapabilitiesFromTraits(List<MachineTrait> traits, Class<T> capability) {
        if (traits.isEmpty()) return Collections.emptyList();
        List<T> list = new ArrayList<>();
        for (MachineTrait trait : traits) {
            if (capability.isInstance(trait)) {
                list.add(capability.cast(trait));
            }
        }
        return list;
    }

    public static <T> List<T> getCapabilitiesFromTraits(List<MachineTrait> traits, Direction accessSide, Class<T> capability) {
        if (traits.isEmpty()) return Collections.emptyList();
        List<T> list = new ArrayList<>();
        for (MachineTrait trait : traits) {
            if (trait instanceof ICapabilityTrait capabilityTrait && capability.isInstance(trait) && capabilityTrait.hasCapability(accessSide)) {
                list.add(capability.cast(trait));
            }
        }
        return list;
    }
}
