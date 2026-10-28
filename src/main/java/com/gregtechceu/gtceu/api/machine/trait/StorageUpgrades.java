package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.item.StorageUpgradeItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/** Upgrade items are private menu inventory, never exposed to automation or mixed with stored contents. */
public final class StorageUpgrades extends MachineTrait {

    public static final String ITEMS_KEY = "storageUpgrades";
    public static final String LIMIT_KEY = "storageCapacityLimit";
    public static final int SLOTS = 4;

    @Getter
    private final StackInventory items;
    private final Runnable changed;
    @Getter
    @SyncToClient(listener = "onTiersSynced")
    private final byte[] tiers;
    private int multiplier = 1;

    public StorageUpgrades(MetaMachine machine, Runnable changed) {
        this(machine, changed, true);
    }

    public StorageUpgrades(MetaMachine machine, Runnable changed, boolean enabled) {
        super(machine);
        this.changed = changed;
        this.tiers = new byte[enabled ? SLOTS : 0];
        this.items = new StackInventory(enabled ? SLOTS : 0) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        items.setFilter(stack -> stack.getItem() instanceof StorageUpgradeItem);
        items.setOnContentsChanged(this::upgradesChanged);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public long capacity(long base) {
        return multiply(base, multiplier);
    }

    public static long multiply(long base, int multiplier) {
        return base > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : base * multiplier;
    }

    public static int multiplier(@Nullable CompoundTag tag) {
        if (tag == null) return 1;
        var upgrades = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        int multiplier = 1;
        for (int i = 0; i < Math.min(SLOTS, upgrades.size()); i++) {
            var stack = ItemStack.of(upgrades.getCompound(i));
            if (!stack.isEmpty() && stack.getItem() instanceof StorageUpgradeItem upgrade) multiplier *= upgrade.getCapacityMultiplier();
        }
        return multiplier;
    }

    public static long capacity(@Nullable CompoundTag tag, long base) {
        long maximum = multiply(base, multiplier(tag));
        return limit(tag, maximum);
    }

    public static long limit(@Nullable CompoundTag tag, long maximum) {
        if (tag == null) return maximum;
        if (tag.contains(LIMIT_KEY)) return Math.clamp(tag.getLong(LIMIT_KEY), 1, maximum);
        // Older compartment items stored their user-selected limit under this key.
        return tag.contains("compartmentLimit") ? Math.clamp(tag.getLong("compartmentLimit"), 1, maximum) : maximum;
    }

    private void upgradesChanged() {
        updateUpgrades();
        changed.run();
    }

    private void updateUpgrades() {
        multiplier = 1;
        for (int slot = 0; slot < items.size(); slot++) {
            var stack = items.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof StorageUpgradeItem upgrade) {
                tiers[slot] = (byte) upgrade.getTier();
                multiplier *= upgrade.getCapacityMultiplier();
            } else {
                tiers[slot] = 0;
            }
        }
    }

    private void onTiersSynced(byte[] value, byte[] previous) {
        multiplier = 1;
        for (byte tier : value) {
            if (tier != 0) multiplier *= StorageUpgradeItem.byTier(tier).getCapacityMultiplier();
        }
    }

    public UIElement createSlots() {
        var row = UIElement.centeredRow(UISizes.SLOT_SIZE);
        for (int slot = 0; slot < items.size(); slot++) row.addChild(ItemSlot.of(items, slot));
        return row;
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        if (items.isEmpty()) return;
        var upgrades = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) upgrades.add(items.getStackInSlot(slot).save(new CompoundTag()));
        tag.put(ITEMS_KEY, upgrades);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        var upgrades = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        for (int slot = 0; slot < items.size(); slot++) {
            items.stacks[slot] = slot < upgrades.size() ? ItemStack.of(upgrades.getCompound(slot)) : ItemStack.EMPTY;
        }
        updateUpgrades();
    }
}
