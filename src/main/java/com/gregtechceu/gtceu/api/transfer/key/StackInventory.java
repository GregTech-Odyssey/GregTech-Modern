package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.datasynclib.GTDataFixer;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import com.gto.datasynclib.AbstractDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.datastream.data.NullData;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * 会原地改 NBT 的单件物品槽（电池、工具、催化剂、器官等），内部保留活体 ItemStack，存档格式与旧 CustomItemStackHandler 一致。
 * 对内以 {@link IKeyHandler} 视图参与传输与配方，对外菜单与 Forge 能力直接读写活体栈。
 */
public class StackInventory extends AbstractDataSerializable implements IKeyHandler<AEItemKey> {

    protected Runnable onContentsChanged = GTUtil.NOOP;
    @SuppressWarnings("unchecked")
    protected Predicate<ItemStack> filter = GTUtil.FAVORABLE;
    public boolean isInputLimited;
    public final ItemStack[] stacks;
    public final int size;

    public StackInventory() {
        this(1);
    }

    public StackInventory(int size) {
        ItemStack[] stacks = new ItemStack[size];
        Arrays.fill(stacks, ItemStack.EMPTY);
        this.stacks = stacks;
        this.size = size;
    }

    public StackInventory(@NotNull ItemStack itemStack) {
        this.stacks = new ItemStack[] { itemStack };
        this.size = 1;
    }

    public StackInventory(@NotNull List<ItemStack> stacks) {
        this.stacks = stacks.toArray(new ItemStack[0]);
        this.size = stacks.size();
    }

    public Runnable getOnContentsChanged() {
        return onContentsChanged;
    }

    public void setOnContentsChanged(@NotNull Runnable onContentsChanged) {
        this.onContentsChanged = onContentsChanged;
    }

    public Predicate<ItemStack> getFilter() {
        return filter;
    }

    public void setFilter(Predicate<ItemStack> filter) {
        this.filter = filter;
    }

    @Override
    public AEKeyType keyType() {
        return AEKeyType.items();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean fixedSize() {
        return true;
    }

    @Override
    public @Nullable AEItemKey keyAt(int slot) {
        return Keys.item(stacks[slot]);
    }

    @Override
    public long amountAt(int slot) {
        return stacks[slot].getCount();
    }

    @Override
    public long slotLimit(int slot) {
        return getSlotLimit(slot);
    }

    @Override
    public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        ItemStack existing = stacks[slot];
        if (!existing.isEmpty() && Keys.itemType(existing) != key) return 0;
        var template = key.getReadOnlyStack();
        if (!isItemValid(slot, template)) return 0;
        int limit = getStackLimit(slot, template) - existing.getCount();
        if (limit < 1) return 0;
        int n = amount < limit ? (int) amount : limit;
        if (!simulate) {
            if (existing.isEmpty()) {
                stacks[slot] = Keys.toStack(key, n);
            } else {
                existing.grow(n);
            }
            onContentsChanged(slot);
        }
        return n;
    }

    @Override
    public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        ItemStack existing = stacks[slot];
        if (existing.isEmpty() || Keys.itemType(existing) != key) return 0;
        return extract(slot, existing, amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount, simulate);
    }

    public final void setStackInSlot(int slot, @NotNull ItemStack stack) {
        this.stacks[slot] = stack;
        onContentsChanged(slot);
    }

    @NotNull
    public final ItemStack getStackInSlot(int slot) {
        return this.stacks[slot];
    }

    public int getSlots() {
        return size;
    }

    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return filter.test(stack) && !(isInputLimited && limitedInsert(slot, stack));
    }

    public int getSlotLimit(int slot) {
        return 64;
    }

    protected int getStackLimit(int slot, @NotNull ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        var count = stack.getCount();
        if (count < 1) return ItemStack.EMPTY;
        var inserted = this.insert(slot, stack, count, simulate);
        if (inserted < 1) return stack;
        if (inserted < count) return stack.copyWithCount(count - inserted);
        return ItemStack.EMPTY;
    }

    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = this.stacks[slot];
        var count = existing.getCount();
        if (count < 1) return ItemStack.EMPTY;
        var extracted = extract(slot, existing, amount, simulate);
        if (extracted < 1) return ItemStack.EMPTY;
        if (!simulate && extracted == count) return existing;
        return existing.copyWithCount(extracted);
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
        if (amount <= 0) return 0;
        int count = existing.getCount();
        if (count < 1) return 0;
        int n = Math.min(count, amount);
        if (!simulate) {
            if (count <= amount) {
                this.stacks[slot] = ItemStack.EMPTY;
            } else {
                existing.setCount(count - amount);
            }
            onExtracted(slot, existing, n);
            onContentsChanged(slot);
        }
        return n;
    }

    protected void onExtracted(int slot, ItemStack stack, int amount) {}

    public void onContentsChanged(int slot) {
        onContentsChanged.run();
        changed = true;
    }

    public void clear() {
        Arrays.fill(stacks, ItemStack.EMPTY);
        onContentsChanged.run();
        changed = true;
    }

    @Override
    public boolean isEmpty() {
        for (var stack : stacks) {
            if (!stack.isEmpty()) return false;
        }
        return true;
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

    @Override
    public void writeBuffer(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var stacks = this.stacks;
        for (int i = 0; i < this.size; i++) {
            if (!stacks[i].isEmpty()) {
                data.writeVarInt(i + 1);
                data.writeItem(stacks[i].copyWithCount(1));
                data.writeVarInt(stacks[i].getCount());
            }
        }
        data.writeVarInt(0);
        data.writeBoolean(this.isInputLimited);
    }

    @Override
    public void readBuffer(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var stacks = this.stacks;
        Arrays.fill(stacks, ItemStack.EMPTY);
        int s;
        while ((s = data.readVarInt()) != 0) {
            var item = data.readItem();
            int count = data.readVarInt();
            int slot = s - 1;
            if (slot < this.size && !item.isEmpty()) {
                item.setCount(count);
                stacks[slot] = item;
            }
        }
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
        GTDataFixer.decodeStackInventory(this, data, dataVersion);
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
