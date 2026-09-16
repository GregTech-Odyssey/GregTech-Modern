package com.gregtechceu.gtceu.api.transfer.item;

import com.gregtechceu.gtceu.datasynclib.GTDataFixer;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.AbstractDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.datastream.data.NullData;
import com.gto.datasynclib.util.DataCodecs;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class CustomItemStackHandler extends AbstractDataSerializable implements ICustomItemStackHandler {

    @NotNull
    @Setter
    @Getter
    protected Runnable onContentsChanged = GTUtil.NOOP;
    @Getter
    @Setter
    protected Predicate<ItemStack> filter = GTUtil.FAVORABLE;

    public boolean isInputLimited;
    public final ItemStack[] stacks;
    public final int size;

    public CustomItemStackHandler() {
        this.stacks = new ItemStack[] { ItemStack.EMPTY };
        this.size = 1;
    }

    public CustomItemStackHandler(int size) {
        ItemStack[] stacks = new ItemStack[size];
        Arrays.fill(stacks, ItemStack.EMPTY);
        this.stacks = stacks;
        this.size = size;
    }

    public CustomItemStackHandler(@NotNull ItemStack itemStack) {
        this.stacks = new ItemStack[] { itemStack };
        this.size = 1;
    }

    public CustomItemStackHandler(@NotNull List<ItemStack> stacks) {
        this.stacks = stacks.toArray(new ItemStack[0]);
        this.size = stacks.size();
    }

    @Override
    public final void setStackInSlot(int slot, @NotNull ItemStack stack) {
        this.stacks[slot] = stack;
        onContentsChanged(slot);
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    @NotNull
    public final ItemStack getStackInSlot(int slot) {
        return this.stacks[slot];
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return filter.test(stack) && !(isInputLimited && limitedInsert(slot, stack));
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    protected int getStackLimit(int slot, @NotNull ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    @NotNull
    public final ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        var count = stack.getCount();
        if (count < 1) return ItemStack.EMPTY;
        var inserted = this.insert(slot, stack, count, simulate);
        if (inserted < 1) return stack;
        if (inserted < count) return stack.copyWithCount(count - inserted);
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack insertItemStacked(@NotNull ItemStack stack, boolean simulate) {
        if (!isInputLimited) {
            return ICustomItemStackHandler.super.insertItemStacked(stack, simulate);
        }
        var remaining = stack;
        for (var slot = 0; slot < size && !remaining.isEmpty(); slot++) {
            var previousCount = remaining.getCount();
            remaining = insertItem(slot, remaining, simulate);
            if (remaining.getCount() < previousCount) {
                break;
            }
        }
        return remaining;
    }

    @Override
    @NotNull
    public final ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = this.stacks[slot];
        var count = existing.getCount();
        if (count < 1) return ItemStack.EMPTY;
        var extracted = extract(slot, existing, amount, simulate);
        if (extracted < 1) return ItemStack.EMPTY;
        if (!simulate && extracted == count) return existing;
        return existing.copyWithCount(extracted);
    }

    @Override
    public int insertExternal(AEItemKey itemKey, int amount, Actionable mode) {
        final var stack = itemKey.getReadOnlyStack();
        final var slotCount = this.getSlots();
        final var simulate = mode == Actionable.SIMULATE;
        var totalInserted = 0;
        for (var i = 0; i < slotCount && amount > 0; i++) {
            final var inserted = this.insert(i, stack, amount, simulate);
            if (inserted > 0) {
                totalInserted += inserted;
                amount -= inserted;
                if (amount <= 0 || isInputLimited) {
                    break;
                }
            }
        }
        return totalInserted;
    }

    @Override
    public int extractExternal(AEItemKey itemKey, int amount, Actionable mode) {
        final var slotCount = this.getSlots();
        final var simulate = mode == Actionable.SIMULATE;
        var totalExtracted = 0;
        for (var i = 0; i < slotCount; i++) {
            var existing = this.stacks[i];
            if (itemKey.matches(existing)) {
                final var extracted = this.extract(i, existing, amount, simulate);
                if (extracted > 0) {
                    totalExtracted += extracted;
                    amount -= extracted;
                    if (amount <= 0) {
                        break;
                    }
                }
            }
        }
        return totalExtracted;
    }

    public int insert(int slot, @NotNull ItemStack stack, int amount, boolean simulate) {
        if (!isItemValid(slot, stack)) return 0;
        ItemStack existing = this.stacks[slot];
        var stored = existing.getCount();
        int limit = getStackLimit(slot, stack) - stored;
        if (limit < 1) return 0;
        if (stored == 0 || canItemStacksStack(stack, existing)) {
            boolean reachedLimit = amount > limit;
            if (!simulate) {
                if (existing.isEmpty()) {
                    this.stacks[slot] = stack.copyWithCount(reachedLimit ? limit : amount);
                } else {
                    existing.grow(reachedLimit ? limit : amount);
                }
                onContentsChanged(slot);
            }
            return reachedLimit ? limit : amount;
        }
        return 0;
    }

    public int extract(int slot, ItemStack existing, int amount, boolean simulate) {
        if (amount == 0) return 0;
        int count = existing.getCount();
        if (count < 1) return 0;
        if (count <= amount) {
            if (!simulate) {
                this.stacks[slot] = ItemStack.EMPTY;
                onContentsChanged(slot);
            }
            return count;
        } else {
            if (!simulate) {
                existing.setCount(count - amount);
                onContentsChanged(slot);
            }
            return amount;
        }
    }

    public void onContentsChanged(int slot) {
        onContentsChanged.run();
        changed = true;
    }

    public void clear() {
        Arrays.fill(stacks, ItemStack.EMPTY);
        onContentsChanged.run();
        changed = true;
    }

    public static boolean canItemStacksStack(@NotNull ItemStack a, @NotNull ItemStack b) {
        var ia = a.getItem();
        if (ia == Items.AIR) return false;
        var ib = b.getItem();
        if (ia == ib) {
            var at = a.getTag();
            var bt = b.getTag();
            if (at == null || at.isEmpty()) return bt == null || bt.isEmpty();
            return at.equals(bt);
        }
        return false;
    }

    public boolean limitedInsert(int index, ItemStack itemStack) {
        for (int i = 0; i < this.size; i++) {
            if (i == index) continue;
            if (stacks[i].getItem() == itemStack.getItem()) {
                return true;
            }
        }
        return false;
    }

    public static void insertItemStackedFast(CustomItemStackHandler inventory, @NotNull ItemStack stack) {
        var item = stack.getItem();
        var amount = stack.getCount();
        for (int slot = 0; slot < inventory.size; ++slot) {
            ItemStack stored = inventory.stacks[slot];
            int count = stored.getCount();
            if (count < 64 && (count == 0 || stored.is(item))) {
                amount -= inventory.insert(slot, stack, amount, false);
                if (amount < 1) break;
            }
        }
    }

    @Override
    public void writeBuffer(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var i = 0;
        var stacks = this.stacks;
        while (i < this.size) {
            if (!stacks[i].isEmpty()) {
                data.writeItem(stacks[i]);
                data.writeVarInt(i);
            }
            i++;
        }
        data.writeByte(-1);
        data.writeBoolean(this.isInputLimited);
    }

    @Override
    public void readBuffer(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var stacks = this.stacks;
        Arrays.fill(stacks, ItemStack.EMPTY);
        while (data.getByte(data.readerIndex()) != -1) {
            var item = data.readItem();
            var slot = data.readVarInt();
            if (slot >= 0 && slot < this.size) {
                stacks[slot] = item;
            }
        }
        data.readByte();
        this.isInputLimited = data.readBoolean();
    }

    @Override
    public Data writeData() {
        var list = new ListData();
        if (this.isInputLimited) list.addNull();
        var stacks = this.stacks;
        for (int i = 0; i < this.size; i++) {
            var stack = stacks[i];
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stack.save(itemTag);
                list.add(DataCodecs.COMPOUND_TAG_CODEC.encode(itemTag));
            }
        }
        return list.isEmpty() ? NullData.INSTANCE : list;
    }

    @Override
    public void readData(@NotNull Data data, int dataVersion) {
        GTDataFixer.decodeCustomItemStackHandler(this, data, dataVersion);
    }

    public final ByteArrayTag serializeNBT() {
        return new ByteArrayTag(writeData().writeToBytes());
    }

    public final void deserializeNBT(Tag tag) {
        if (tag instanceof ByteArrayTag byteTags) {
            readData(Data.readData(byteTags.getAsByteArray()), GTDataFixer.VERSION);
        } else if (tag instanceof CompoundTag nbt) {
            ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < tagList.size(); i++) {
                CompoundTag itemTags = tagList.getCompound(i);
                int slot = itemTags.getInt("Slot");
                if (slot >= 0 && slot < size) {
                    stacks[slot] = ItemStack.of(itemTags);
                }
            }
            isInputLimited = nbt.getBoolean("il");
        }
    }
}
