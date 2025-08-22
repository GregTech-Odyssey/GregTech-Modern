package com.gregtechceu.gtceu.api.transfer.item;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

public class SingleCustomItemStackHandler extends CustomItemStackHandler {

    public SingleCustomItemStackHandler(int size) {
        super(size);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected int getStackLimit(int slot, @NotNull ItemStack stack) {
        return 1;
    }
}
