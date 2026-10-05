package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyIntMap;
import appeng.hooks.IUnique;
import org.jetbrains.annotations.Nullable;

public final class Circuits {

    public static final int MAX = IntCircuitBehaviour.CIRCUIT_MAX;
    public static final String CONFIGURATION = "Configuration";

    private Circuits() {}

    private static final class Registered {

        private static final AEItemKey[] KEYS = new AEItemKey[MAX + 1];
        private static final AEKeyIntMap<AEItemKey> CONFIGURATIONS = new AEKeyIntMap<>(MAX * 2);
        private static final Item ITEM = GTItems.PROGRAMMED_CIRCUIT.get();
        private static final int UID = IUnique.getUid(ITEM);

        static {
            for (int i = 0; i < MAX; i++) {
                var k = AEItemKey.of(IntCircuitBehaviour.stack(i));
                KEYS[i] = k;
                CONFIGURATIONS.put(k, i);
            }
        }
    }

    public static Item item() {
        return Registered.ITEM;
    }

    public static int uid() {
        return Registered.UID;
    }

    public static AEItemKey key(int configuration) {
        return Registered.KEYS[configuration];
    }

    public static int configOf(@Nullable AEItemKey key) {
        if (key == null || key.uid != Registered.UID) return -1;
        return Registered.CONFIGURATIONS.getInt(key);
    }

    public static int configOf(ItemStack stack) {
        if (stack.getItem() != Registered.ITEM) return -1;
        return configOf(stack.getTag());
    }

    public static int configOf(@Nullable CompoundTag tag) {
        if (tag != null && tag.get(CONFIGURATION) instanceof NumericTag n) {
            int c = n.getAsInt();
            if (c >= 0 && c <= MAX) return c;
        }
        return -1;
    }

    public static void set(KeyInventory<AEItemKey> inventory, int slot, int configuration) {
        if (configuration < 0) {
            inventory.set(slot, null, 0);
        } else {
            inventory.set(slot, key(configuration), 1);
        }
    }

    public static int get(KeyInventory<AEItemKey> inventory, int slot) {
        return configOf(inventory.keyAt(slot));
    }
}
