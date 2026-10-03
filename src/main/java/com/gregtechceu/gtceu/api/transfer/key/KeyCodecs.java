package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.gto.datasynclib.DataSyncCodec;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.datastream.codec.DataCodec;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.NotNull;

public final class KeyCodecs {

    private KeyCodecs() {}

    public static final DataCodec<AEKey> AE_KEY_DATA_CODEC = new DataCodec<>() {

        @Override
        public AEKey decode(@NotNull Data data, int dataVersion) {
            return AEKey.fromTagGeneric(DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
        }

        @Override
        public @NotNull Data encode(AEKey obj) {
            return DataCodecs.COMPOUND_TAG_CODEC.encode(obj.toTagGeneric());
        }
    };

    public static final DataCodec<AEItemKey> AE_ITEM_KEY_DATA_CODEC = new DataCodec<>() {

        @Override
        public AEItemKey decode(@NotNull Data data, int dataVersion) {
            return AEItemKey.fromTag(DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
        }

        @Override
        public @NotNull Data encode(AEItemKey obj) {
            return DataCodecs.COMPOUND_TAG_CODEC.encode(obj.toTag());
        }
    };

    public static final DataCodec<AEFluidKey> AE_FLUID_KEY_DATA_CODEC = new DataCodec<>() {

        @Override
        public AEFluidKey decode(@NotNull Data data, int dataVersion) {
            return AEFluidKey.fromTag(DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
        }

        @Override
        public @NotNull Data encode(AEFluidKey obj) {
            return DataCodecs.COMPOUND_TAG_CODEC.encode(obj.toTag());
        }
    };

    public static final DataCodec<GenericStack> GENERIC_STACK_DATA_CODEC = new DataCodec<>() {

        @Override
        public GenericStack decode(@NotNull Data data, int dataVersion) {
            return GenericStack.readTag(DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
        }

        @Override
        public @NotNull Data encode(GenericStack obj) {
            return DataCodecs.COMPOUND_TAG_CODEC.encode(GenericStack.writeTag(obj));
        }
    };

    public static final DataCodec<KeyCounter> KEY_COUNTER_DATA_CODEC = new DataCodec<>() {

        @Override
        public KeyCounter decode(@NotNull Data data, int dataVersion) {
            var list = data.asListData();
            var keyCounter = new KeyCounter();
            keyCounter.ensureCapacity(list.size());
            for (var entryTag : list) {
                var tag = DataCodecs.COMPOUND_TAG_CODEC.decode(entryTag, dataVersion);
                var what = AEKey.fromTagGeneric(tag);
                long amount = tag.getLong("#");
                if (what != null) {
                    keyCounter.add(what, amount);
                }
            }
            return keyCounter;
        }

        @Override
        public @NotNull Data encode(KeyCounter obj) {
            var list = new ListData(obj.size());
            for (var entry : obj) {
                var tag = entry.getKey().toTagGeneric();
                tag.putLong("#", entry.getLongValue());
                list.add(DataCodecs.COMPOUND_TAG_CODEC.encode(tag));
            }
            return list;
        }
    };

    public static final ByteStreamCodec<AEKey> AE_KEY_STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, AEKey obj) {
            AEKey.writeKey(buf, obj);
        }

        @Override
        public AEKey decode(FriendlyByteBuf buf) {
            return AEKey.readKey(buf);
        }
    };

    public static final ByteStreamCodec<AEItemKey> AE_ITEM_KEY_STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, AEItemKey obj) {
            obj.writeToPacket(buf);
        }

        @Override
        public AEItemKey decode(FriendlyByteBuf buf) {
            return AEItemKey.fromPacket(buf);
        }
    };

    public static final ByteStreamCodec<AEFluidKey> AE_FLUID_KEY_STREAM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, AEFluidKey obj) {
            obj.writeToPacket(buf);
        }

        @Override
        public AEFluidKey decode(FriendlyByteBuf buf) {
            return AEFluidKey.fromPacket(buf);
        }
    };

    public static final ByteStreamCodec<GenericStack> GENERIC_STACK_STREAM_CODEC = new ByteStreamCodec<>() {

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

    public static final ByteStreamCodec<KeyCounter> KEY_COUNTER_STREAM_CODEC = new ByteStreamCodec<>() {

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
