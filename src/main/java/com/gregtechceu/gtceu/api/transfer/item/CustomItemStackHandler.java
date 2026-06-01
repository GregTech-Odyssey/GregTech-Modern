package com.gregtechceu.gtceu.api.transfer.item;

import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;

import com.gto.datasynclib.AbstractDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
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

    public ItemStack[] stacks;
    public boolean isInputLimited;
    public int size;

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

    public void setSize(int size) {
        if (size != stacks.length) {
            ItemStack[] stacks = new ItemStack[size];
            Arrays.fill(stacks, ItemStack.EMPTY);
            this.stacks = stacks;
            this.size = size;
        }
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        this.stacks[slot] = stack;
        onContentsChanged(slot);
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        return this.stacks[slot];
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        var count = stack.getCount();
        if (count < 1) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;
        ItemStack existing = this.stacks[slot];
        var stored = existing.getCount();
        int limit = getStackLimit(slot, stack) - stored;
        if (limit < 1) return stack;
        if (stored == 0 || canItemStacksStack(stack, existing)) {
            boolean reachedLimit = count > limit;
            if (!simulate) {
                if (existing.isEmpty()) {
                    this.stacks[slot] = stack.copyWithCount(reachedLimit ? limit : count);
                } else {
                    existing.grow(reachedLimit ? limit : count);
                }
                onContentsChanged(slot);
            }
            return reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, count - limit) : ItemStack.EMPTY;
        }
        return stack;
    }

    public int insert(int slot, @NotNull ItemStack stack, int amount, boolean simulate) {
        if (!filter.test(stack)) return 0;
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
            }
            return reachedLimit ? limit : amount;
        }
        return 0;
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) return ItemStack.EMPTY;
        ItemStack existing = this.stacks[slot];
        int count = existing.getCount();
        if (count < 1) return ItemStack.EMPTY;
        if (count <= amount) {
            if (simulate) {
                return existing.copy();
            } else {
                this.stacks[slot] = ItemStack.EMPTY;
                onContentsChanged(slot);
                return existing;
            }
        } else {
            if (!simulate) {
                existing.setCount(count - amount);
                onContentsChanged(slot);
            }
            return ItemHandlerHelper.copyStackWithSize(existing, amount);
        }
    }

    public int extract(int slot, int amount, boolean simulate) {
        if (amount == 0) return 0;
        ItemStack existing = this.stacks[slot];
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

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    protected int getStackLimit(int slot, @NotNull ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return filter.test(stack) && !(isInputLimited && limitedInsert(slot, stack));
    }

    public void onContentsChanged(int slot) {
        onContentsChanged.run();
        syncChange = true;
    }

    public void clear() {
        Arrays.fill(stacks, ItemStack.EMPTY);
        onContentsChanged.run();
        syncChange = true;
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

    public CompoundTag serializeNBT() {
        ListTag nbtTagList = new ListTag();
        for (int i = 0; i < size; i++) {
            if (!stacks[i].isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stacks[i].save(itemTag);
                nbtTagList.add(itemTag);
            }
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", nbtTagList);
        nbt.putInt("Size", size);
        nbt.putBoolean("il", isInputLimited);
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        setSize(Math.max(size, nbt.getInt("Size")));
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

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        data.writeVarInt(size);
        var i = 0;
        while (i < size) {
            if (!stacks[i].isEmpty()) {
                data.writeItem(stacks[i]);
                data.writeVarInt(i);
            }
            i++;
        }
        data.writeByte(-1);
        data.writeBoolean(isInputLimited);
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        setSize(Math.max(size, data.readVarInt()));
        while (data.getByte(data.readerIndex()) != -1) {
            var item = data.readItem();
            var slot = data.readVarInt();
            if (slot >= 0 && slot < size) {
                stacks[slot] = item;
            }
        }
        data.readByte();
        isInputLimited = data.readBoolean();
    }

    @Override
    public Data writeData() {
        return DataCodecs.COMPOUND_TAG_CODEC.encode(serializeNBT());
    }

    @Override
    public void readData(@NotNull Data data, int dataVersion) {
        deserializeNBT(DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
    }
}
