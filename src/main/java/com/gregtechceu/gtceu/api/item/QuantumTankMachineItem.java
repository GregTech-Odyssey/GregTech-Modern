package com.gregtechceu.gtceu.api.item;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.misc.forge.QuantumFluidHandlerItemStack;
import com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import org.jetbrains.annotations.Nullable;

public class QuantumTankMachineItem extends MetaMachineItem {

    public QuantumTankMachineItem(MetaMachineBlock block, Properties properties) {
        super(block, properties);
    }

    public static QuantumTankMachineItem create(MetaMachineBlock block, Properties properties) {
        return new QuantumTankMachineItem(block, properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new QuantumFluidHandlerItemStack(stack, QuantumTankMachine.TANK_CAPACITY.getLong(getDefinition()));
    }
}
