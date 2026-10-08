package com.gregtechceu.gtceu.uipro.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.datastream.codec.StreamCodec;

public record SyncItem(ItemStack stack) {

    public static final SyncItem EMPTY = new SyncItem(ItemStack.EMPTY);

    public static final StreamCodec<FriendlyByteBuf, SyncItem> CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, SyncItem value) {
            buf.writeItem(value.stack.isEmpty() ? ItemStack.EMPTY : value.stack.copyWithCount(1));
        }

        @Override
        public SyncItem decode(FriendlyByteBuf buf) {
            var stack = buf.readItem();
            return stack.isEmpty() ? EMPTY : new SyncItem(stack);
        }
    };

    public static SyncItem of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new SyncItem(stack.copyWithCount(1));
    }

    public boolean matches(ItemStack other) {
        return other == null || other.isEmpty() ? stack.isEmpty() : ItemStack.isSameItemSameTags(stack, other);
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
