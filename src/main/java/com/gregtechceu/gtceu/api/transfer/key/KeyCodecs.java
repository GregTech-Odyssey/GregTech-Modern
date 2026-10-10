package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.gto.datasynclib.DataSyncCodec;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.datastream.codec.ValueCodec;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ValueCodecs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class KeyCodecs {

    private KeyCodecs() {}

    public static final ValueCodec<AEKey> AE_KEY_DATA_CODEC = new ValueCodec<>() {

        @Override
        public AEKey decode(ValueOps ops, @NotNull Object data) {
            return AEKey.fromTagGeneric(ValueCodecs.COMPOUND_TAG.decode(ops, data));
        }

        @Override
        public @NotNull Object encode(ValueOps ops, AEKey obj) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, obj.toTagGeneric());
        }
    };

    public static final ValueCodec<AEItemKey> AE_ITEM_KEY_DATA_CODEC = new ValueCodec<>() {

        @Override
        public AEItemKey decode(ValueOps ops, @NotNull Object data) {
            return AEItemKey.fromTag(ValueCodecs.COMPOUND_TAG.decode(ops, data));
        }

        @Override
        public @NotNull Object encode(ValueOps ops, AEItemKey obj) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, obj.toTag());
        }
    };

    public static final ValueCodec<AEFluidKey> AE_FLUID_KEY_DATA_CODEC = new ValueCodec<>() {

        @Override
        public AEFluidKey decode(ValueOps ops, @NotNull Object data) {
            return AEFluidKey.fromTag(ValueCodecs.COMPOUND_TAG.decode(ops, data));
        }

        @Override
        public @NotNull Object encode(ValueOps ops, AEFluidKey obj) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, obj.toTag());
        }
    };

    public static final ValueCodec<GenericStack> GENERIC_STACK_DATA_CODEC = new ValueCodec<>() {

        @Override
        public GenericStack decode(ValueOps ops, @NotNull Object data) {
            return GenericStack.readTag(ValueCodecs.COMPOUND_TAG.decode(ops, data));
        }

        @Override
        public @NotNull Object encode(ValueOps ops, GenericStack obj) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, GenericStack.writeTag(obj));
        }
    };

    public static final ValueCodec<KeyCounter> KEY_COUNTER_DATA_CODEC = new ValueCodec<>() {

        @Override
        public KeyCounter decode(ValueOps ops, @NotNull Object data) {
            var list = ops.getList(data);
            var keyCounter = new KeyCounter();
            keyCounter.ensureCapacity(list.size());
            for (var entryTag : list) {
                var tag = ValueCodecs.COMPOUND_TAG.decode(ops, entryTag);
                var what = AEKey.fromTagGeneric(tag);
                long amount = tag.getLong("#");
                if (what != null) {
                    keyCounter.add(what, amount);
                }
            }
            return keyCounter;
        }

        @Override
        public @NotNull Object encode(ValueOps ops, KeyCounter obj) {
            var list = new ArrayList<Object>(obj.size());
            for (var entry : obj) {
                var tag = entry.getKey().toTagGeneric();
                tag.putLong("#", entry.getLongValue());
                list.add(ValueCodecs.COMPOUND_TAG.encode(ops, tag));
            }
            return ops.createList(list);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, AEKey> AE_KEY_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, AEKey obj) {
            AEKey.writeKey(buf, obj);
        }

        @Override
        public AEKey decode(FriendlyByteBuf buf) {
            return AEKey.readKey(buf);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, AEItemKey> AE_ITEM_KEY_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, AEItemKey obj) {
            obj.writeToPacket(buf);
        }

        @Override
        public AEItemKey decode(FriendlyByteBuf buf) {
            return AEItemKey.fromPacket(buf);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, AEFluidKey> AE_FLUID_KEY_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, AEFluidKey obj) {
            obj.writeToPacket(buf);
        }

        @Override
        public AEFluidKey decode(FriendlyByteBuf buf) {
            return AEFluidKey.fromPacket(buf);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, GenericStack> GENERIC_STACK_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, GenericStack obj) {
            AEKey.writeKey(buf, obj.what());
            buf.writeVarLong(obj.amount());
        }

        @Override
        public GenericStack decode(FriendlyByteBuf buf) {
            var what = AEKey.readKey(buf);
            long amount = buf.readVarLong();
            return what == null ? null : new GenericStack(what, amount);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, KeyCounter> KEY_COUNTER_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, KeyCounter obj) {
            buf.writeVarInt(obj.size());
            for (var entry : obj) {
                AEKey.writeKey(buf, entry.getKey());
                buf.writeVarLong(entry.getLongValue());
            }
        }

        @Override
        public KeyCounter decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            var keyCounter = new KeyCounter();
            keyCounter.ensureCapacity(size);
            for (int i = 0; i < size; i++) {
                var what = AEKey.readKey(buf);
                long amount = buf.readVarLong();
                if (what != null) {
                    keyCounter.add(what, amount);
                }
            }
            return keyCounter;
        }
    };

    public static void register() {
        DataSyncCodec.register(AEKey.class, AE_KEY_STREAM_CODEC, AE_KEY_DATA_CODEC);
        DataSyncCodec.register(AEItemKey.class, AE_ITEM_KEY_STREAM_CODEC, AE_ITEM_KEY_DATA_CODEC);
        DataSyncCodec.register(AEFluidKey.class, AE_FLUID_KEY_STREAM_CODEC, AE_FLUID_KEY_DATA_CODEC);
        DataSyncCodec.register(GenericStack.class, GENERIC_STACK_STREAM_CODEC, GENERIC_STACK_DATA_CODEC);
        DataSyncCodec.register(KeyCounter.class, KEY_COUNTER_STREAM_CODEC, KEY_COUNTER_DATA_CODEC);
    }
}
