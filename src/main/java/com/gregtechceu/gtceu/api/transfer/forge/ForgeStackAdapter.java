package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.transfer.key.StackInventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

public final class ForgeStackAdapter implements IItemHandlerModifiable {

    private final StackInventory inventory;
    private static final BooleanSupplier ALWAYS = () -> true;
    private final BooleanSupplier allowInsert;
    private final BooleanSupplier allowExtract;

    public ForgeStackAdapter(StackInventory inventory, BooleanSupplier allowInsert, BooleanSupplier allowExtract) {
        this.inventory = inventory;
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
    }

    public ForgeStackAdapter(StackInventory inventory) {
        this(inventory, ALWAYS, ALWAYS);
    }

    public StackInventory getInventory() {
        return inventory;
    }

    @Override
    public int getSlots() {
        return inventory.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return allowInsert.getAsBoolean() ? inventory.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return allowExtract.getAsBoolean() ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return inventory.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
    }
}
