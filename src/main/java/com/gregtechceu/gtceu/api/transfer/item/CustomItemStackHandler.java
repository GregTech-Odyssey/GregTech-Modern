package com.gregtechceu.gtceu.api.transfer.item;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class CustomItemStackHandler extends ItemStackHandler implements IContentChangeAware, ITagSerializable<CompoundTag> {

    @NotNull
    protected Runnable onContentsChanged = () -> {};
    protected Predicate<ItemStack> filter = stack -> true;

    public CustomItemStackHandler() {
        super();
    }

    public CustomItemStackHandler(int size) {
        super(size);
    }

    public CustomItemStackHandler(ItemStack itemStack) {
        this(NonNullList.of(ItemStack.EMPTY, itemStack));
    }

    public CustomItemStackHandler(NonNullList<ItemStack> stacks) {
        super(stacks);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return filter.test(stack);
    }

    @Override
    public void onContentsChanged(int slot) {
        onContentsChanged.run();
    }

    public void clear() {
        stacks.clear();
        onContentsChanged.run();
    }

    @NotNull
    public Runnable getOnContentsChanged() {
        return this.onContentsChanged;
    }

    public void setOnContentsChanged(@NotNull final Runnable onContentsChanged) {
        if (onContentsChanged == null) {
            throw new NullPointerException("onContentsChanged is marked non-null but is null");
        }
        this.onContentsChanged = onContentsChanged;
    }

    public Predicate<ItemStack> getFilter() {
        return this.filter;
    }

    public void setFilter(final Predicate<ItemStack> filter) {
        this.filter = filter;
    }
}
