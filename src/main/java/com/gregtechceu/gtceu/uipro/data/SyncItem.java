package com.gregtechceu.gtceu.uipro.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record SyncItem(ItemStack stack) {

    public static final SyncItem EMPTY = new SyncItem(ItemStack.EMPTY);

    public static final SyncValue.Codec<SyncItem> CODEC = new SyncValue.Codec<>() {

        @Override
        public void write(FriendlyByteBuf buf, SyncItem value) {
            buf.writeItem(value.stack.isEmpty() ? ItemStack.EMPTY : value.stack.copyWithCount(1));
        }

        @Override
        public SyncItem read(FriendlyByteBuf buf) {
            return of(buf.readItem());
        }
    };

    public static SyncItem of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new SyncItem(stack);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SyncItem other && ItemStack.isSameItemSameTags(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return stack.getItem().hashCode();
    }
}
