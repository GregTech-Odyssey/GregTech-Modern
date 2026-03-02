package com.gregtechceu.gtceu.api.codec;

import com.gregtechceu.gtceu.api.codec.stream.ByteDataStream;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ByteStreamUtil {

    private static final Map<Class<?>, ByteStreamCodec<?>> CODECS = new Reference2ReferenceOpenHashMap<>();

    public static final ByteStreamCodec<ResourceLocation> RESOURCE_LOCATION_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull ResourceLocation obj, ByteDataStream stream) {
            stream.writeUTF(obj.getNamespace());
            stream.writeUTF(obj.getPath());
        }

        @Override
        public @NotNull ResourceLocation decode(ByteDataStream stream) {
            return new ResourceLocation(stream.readUTF(), stream.readUTF());
        }

        static {
            registerCodec(ResourceLocation.class, RESOURCE_LOCATION_CODEC);
        }
    };

    public static final ByteStreamCodec<Item> ITEM_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Item obj, ByteDataStream stream) {
            RESOURCE_LOCATION_CODEC.encode(GTUtil.ITEM_ID.apply(obj), stream);
        }

        @Override
        public @NotNull Item decode(ByteDataStream stream) {
            return GTUtil.ITEM_VALUE.apply(RESOURCE_LOCATION_CODEC.decode(stream));
        }

        static {
            registerCodec(Item.class, ITEM_CODEC);
        }
    };

    public static final ByteStreamCodec<Fluid> FLUID_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Fluid obj, ByteDataStream stream) {
            RESOURCE_LOCATION_CODEC.encode(GTUtil.FLUID_ID.apply(obj), stream);
        }

        @Override
        public @NotNull Fluid decode(ByteDataStream stream) {
            return GTUtil.FLUID_VALUE.apply(RESOURCE_LOCATION_CODEC.decode(stream));
        }

        static {
            registerCodec(Fluid.class, FLUID_CODEC);
        }
    };

    public static final ByteStreamCodec<CompoundTag> COMPOUND_TAG_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull CompoundTag obj, ByteDataStream stream) {
            stream.writeVarInt(obj.size());
            obj.tags.forEach((k, v) -> {
                stream.writeUTF(k);
                TAG_CODEC.encode(v, stream);
            });
        }

        @Override
        public @NotNull CompoundTag decode(ByteDataStream stream) {
            int size = stream.readVarInt();
            CompoundTag compoundTag = new CompoundTag(new O2OOpenCacheHashMap<>(size));
            for (int i = 0; i < size; i++) {
                compoundTag.put(stream.readUTF(), TAG_CODEC.decode(stream));
            }
            return compoundTag;
        }

        static {
            registerCodec(CompoundTag.class, COMPOUND_TAG_CODEC);
        }
    };

    public static final ByteStreamCodec<ListTag> LIST_TAG_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull ListTag obj, ByteDataStream stream) {
            int type = obj.getElementType();
            if (type != 0) {
                stream.writeVarInt(obj.size());
                stream.writeByte(type);
                for (Tag tag : obj) {
                    encodeTag(tag, stream);
                }
            } else {
                stream.writeVarInt(0);
            }
        }

        @Override
        public @NotNull ListTag decode(ByteDataStream stream) {
            ListTag listTag = new ListTag();
            int size = stream.readVarInt();
            if (size != 0) {
                int type = stream.readByte();
                for (int i = 0; i < size; i++) {
                    listTag.add(decodeTag(type, stream));
                }
            }
            return listTag;
        }

        static {
            registerCodec(ListTag.class, LIST_TAG_CODEC);
        }
    };

    public static final ByteStreamCodec<Tag> TAG_CODEC = new ByteStreamCodec<>() {

        @Override
        public void encode(@NotNull Tag obj, ByteDataStream stream) {
            stream.writeByte(obj.getId());
            encodeTag(obj, stream);
        }

        @Override
        public @NotNull Tag decode(ByteDataStream stream) {
            return decodeTag(stream.readByte(), stream);
        }

        static {
            registerCodec(Tag.class, TAG_CODEC);
        }
    };

    public static <T> ByteStreamCodec<T> getCodec(Class<T> type) {
        return (ByteStreamCodec<T>) CODECS.get(type);
    }

    public static synchronized <T> void registerCodec(Class<T> type, ByteStreamCodec<T> codec) {
        CODECS.put(type, codec);
    }

    public static <T> ByteArrayTag toTag(@NotNull T obj, ByteStreamEncoder<T> encoder) {
        return new ByteArrayTag(encoder.encodeToBytes(obj));
    }

    @Nullable
    public static <T> T fromTag(@Nullable Tag tag, ByteStreamDecoder<T> decoder) {
        if (tag instanceof ByteArrayTag arrayTag) return decoder.decodeFromBytes(arrayTag.getAsByteArray());
        return null;
    }

    public static void encodeTag(Tag tag, ByteDataStream stream) {
        switch (tag) {
            case ByteTag byteTag -> stream.writeByte(byteTag.getAsByte());
            case ShortTag shortTag -> stream.writeShort(shortTag.getAsShort());
            case IntTag intTag -> stream.writeInt(intTag.getAsInt());
            case LongTag longTag -> stream.writeLong(longTag.getAsLong());
            case FloatTag floatTag -> stream.writeFloat(floatTag.getAsFloat());
            case DoubleTag doubleTag -> stream.writeDouble(doubleTag.getAsDouble());
            case ByteArrayTag byteArrayTag -> stream.writeByteArray(byteArrayTag.getAsByteArray());
            case StringTag stringTag -> stream.writeUTF(stringTag.getAsString());
            case ListTag listTag -> LIST_TAG_CODEC.encode(listTag, stream);
            case CompoundTag compoundTag -> COMPOUND_TAG_CODEC.encode(compoundTag, stream);
            case IntArrayTag intArrayTag -> stream.writeIntArray(intArrayTag.getAsIntArray());
            case LongArrayTag longArrayTag -> stream.writeLongArray(longArrayTag.getAsLongArray());
            default -> throw new IllegalArgumentException("Unknown tag type: " + tag);
        }
    }

    public static @NotNull Tag decodeTag(int id, ByteDataStream stream) {
        return switch (id) {
            case 1 -> ByteTag.valueOf(stream.readByte());
            case 2 -> ShortTag.valueOf(stream.readShort());
            case 3 -> IntTag.valueOf(stream.readInt());
            case 4 -> LongTag.valueOf(stream.readLong());
            case 5 -> FloatTag.valueOf(stream.readFloat());
            case 6 -> DoubleTag.valueOf(stream.readDouble());
            case 7 -> new ByteArrayTag(stream.readByteArray());
            case 8 -> StringTag.valueOf(stream.readUTF());
            case 9 -> LIST_TAG_CODEC.decode(stream);
            case 10 -> COMPOUND_TAG_CODEC.decode(stream);
            case 11 -> new IntArrayTag(stream.readIntArray());
            case 12 -> new LongArrayTag(stream.readLongArray());
            default -> throw new IllegalArgumentException("Unknown tag type: " + stream.readByte());
        };
    }
}
