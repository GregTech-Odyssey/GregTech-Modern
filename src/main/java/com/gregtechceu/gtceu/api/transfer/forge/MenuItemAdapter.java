package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * 菜单槽位对 {@link KeyInventory} 的视图：菜单逻辑会直接改槽内栈，下次访问时按视图写回存储；
 * set 之后丢弃旧视图（它可能已被交换到光标上），之后的修改不再影响存储。
 */
public final class MenuItemAdapter implements IItemHandlerModifiable {

    private final KeyInventory<AEItemKey> inventory;
    private ItemStack[] views = new ItemStack[0];
    private AEItemKey[] viewKeys = new AEItemKey[0];
    private int[] viewCounts = new int[0];
    private CompoundTag[] viewTags = new CompoundTag[0];

    public MenuItemAdapter(KeyInventory<AEItemKey> inventory) {
        this.inventory = inventory;
    }

    public KeyInventory<AEItemKey> getInventory() {
        return inventory;
    }

    @Override
    public int getSlots() {
        return inventory.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        writeBack(slot);
        var key = inventory.keyAt(slot);
        if (key == null) {
            if (slot < views.length) views[slot] = null;
            return ItemStack.EMPTY;
        }
        int count = Keys.saturatedInt(inventory.amountAt(slot));
        ensure(slot);
        var view = views[slot];
        if (view == null || viewKeys[slot] != key) {
            view = key.toStack(count);
            views[slot] = view;
            viewKeys[slot] = key;
            viewTags[slot] = view.getTag();
        } else if (view.getCount() != count) {
            view.setCount(count);
        }
        viewCounts[slot] = count;
        return view;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (slot < views.length) views[slot] = null;
        inventory.set(slot, Keys.item(stack), stack.getCount());
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;
        writeBack(slot);
        var key = Keys.item(stack);
        if (key == null) return stack;
        long n = inventory.insert(slot, key, stack.getCount(), simulate);
        if (n <= 0) return stack;
        if (!simulate && slot < views.length) views[slot] = null;
        return n >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) n);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        writeBack(slot);
        var key = inventory.keyAt(slot);
        if (key == null) return ItemStack.EMPTY;
        long n = inventory.extract(slot, key, amount, simulate);
        if (n <= 0) return ItemStack.EMPTY;
        if (!simulate && slot < views.length) views[slot] = null;
        return key.toStack((int) n);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Keys.saturatedInt(inventory.slotLimit(slot));
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        var key = Keys.itemType(stack);
        return key != null && inventory.acceptsEmpty(slot, key);
    }

    private void ensure(int slot) {
        if (slot >= views.length) {
            int n = Math.max(slot + 1, inventory.size());
            views = Arrays.copyOf(views, n);
            viewKeys = Arrays.copyOf(viewKeys, n);
            viewCounts = Arrays.copyOf(viewCounts, n);
            viewTags = Arrays.copyOf(viewTags, n);
        }
    }

    public void flush(int slot) {
        writeBack(slot);
    }

    private void writeBack(int slot) {
        if (slot >= views.length) return;
        var view = views[slot];
        if (view == null) return;
        int now = view.getCount();
        if (now == viewCounts[slot] && view.getTag() == viewTags[slot] && (now <= 0 || view.getItem() == viewKeys[slot].getItem())) return;
        views[slot] = null;
        if (now <= 0) {
            inventory.set(slot, null, 0);
        } else {
            inventory.set(slot, Keys.item(view), now);
        }
    }
}
