package com.gto.datasynclib;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.datasream.codec.*;
import com.gto.datasynclib.datasream.data.*;
import com.gto.datasynclib.util.DataCodecs;
import com.gto.datasynclib.util.HashUtil;
import com.gto.datasynclib.util.StreamCodecs;
import com.gto.datasynclib.util.cache.ConcurrentHashMapCache;
import com.gto.datasynclib.util.cache.MapCache;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.UUID;

public final class CombinationCodec<T> {

    private static final Reference2ReferenceOpenHashMap<Class<?>, CombinationCodec<?>> CODECS = new Reference2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceOpenHashMap<Class<?>, HashMap<Object, CombinationCodec<?>>> GENERIC_CODECS = new Reference2ReferenceOpenHashMap<>();

    private static final MapCache<Class<?>, CombinationCodec<?>> ENUM_CACHE = new ConcurrentHashMapCache<>(t -> {
        if (t.isEnum()) {
            var constants = t.getEnumConstants();
            return new CombinationCodec<>((buf, obj) -> buf.writeVarInt(obj.ordinal()),
                    buf -> (Enum<?>) constants[buf.readVarInt()],
                    obj -> IntData.valueOf(obj.ordinal()),
                    data -> (Enum<?>) constants[data.getInt()]);
        }
        throw new RuntimeException("No codec registered for type " + t);
    });

    private static final MapCache<Class<?>, CombinationCodec<?>> ARRAY_CACHE = new ConcurrentHashMapCache<>(t -> {
        if (t.isArray() && !t.componentType().isPrimitive()) {
            Class type = t.getComponentType();
            CombinationCodec<Object> codec = get(type);
            return new CombinationCodec<>(
                    (buf, obj) -> {
                        buf.writeVarInt(obj.length);
                        for (var element : obj) {
                            if (element == null) {
                                buf.writeBoolean(false);
                            } else {
                                buf.writeBoolean(true);
                                codec.streamWriter.encode(buf, element);
                            }
                        }
                    },
                    buf -> {
                        var length = buf.readVarInt();
                        var array = (Object[]) Array.newInstance(type, length);
                        for (int i = 0; i < length; i++) {
                            if (buf.readBoolean()) array[i] = codec.streamReader.decode(buf);
                        }
                        return array;
                    },
                    obj -> {
                        var list = new ListData();
                        for (Object element : obj) {
                            if (element != null) {
                                list.add(codec.dataWriter.encode(element));
                            } else {
                                list.addNull();
                            }
                        }
                        return list;
                    },
                    data -> {
                        var list = data.getList();
                        var size = list.size();
                        var array = (Object[]) Array.newInstance(type, size);
                        for (int i = 0; i < size; i++) {
                            var d = list.get(i);
                            if (d != NullData.INSTANCE) {
                                array[i] = codec.dataReader.decode(d);
                            }
                        }
                        return array;
                    });
        }
        throw new RuntimeException("No codec registered for type " + t);
    });

    public final ByteStreamEncoder<? super T> streamWriter;
    public final ByteStreamDecoder<? extends T> streamReader;
    public final DataEncoder<? super T> dataWriter;
    public final DataDecoder<? extends T> dataReader;

    private CombinationCodec(ByteStreamEncoder<? super T> streamWriter, ByteStreamDecoder<? extends T> streamReader, DataEncoder<? super T> dataWriter, DataDecoder<? extends T> dataReader) {
        this.streamWriter = streamWriter;
        this.streamReader = streamReader;
        this.dataWriter = dataWriter;
        this.dataReader = dataReader;
    }

    public static <T> CombinationCodec<T> of(ByteStreamEncoder<? super T> streamWriter, ByteStreamDecoder<? extends T> streamReader, DataEncoder<? super T> dataWriter, DataDecoder<? extends T> dataReader) {
        return new CombinationCodec<>(streamWriter, streamReader, dataWriter, dataReader);
    }

    public static <T> CombinationCodec<T> of(ByteStreamCodec<T> streamCodec, DataCodec<T> dataCodec) {
        return new CombinationCodec<>(streamCodec, streamCodec, dataCodec, dataCodec);
    }

    public static <T> CombinationCodec<T> of(ByteStreamCodec<T> streamCodec) {
        return of(streamCodec, DataCodec.of(streamCodec));
    }

    public static <T> CombinationCodec<T> of(DataCodec<T> dataCodec) {
        return of(ByteStreamCodec.of(dataCodec), dataCodec);
    }

    /**
     * Checks if a codec is registered for the given type
     *
     * @param type the class to check
     * @return true if codec exists, false otherwise
     */
    public static boolean contains(Class<?> type) {
        return get(type) != null;
    }

    /**
     * Checks if a generic codec is registered for the given type with specific generic parameters
     *
     * @param type         the raw class type
     * @param genericTypes the generic type parameters
     * @return true if codec exists, false otherwise
     */
    public static boolean contains(Class<?> type, Class<?>... genericTypes) {
        var map = GENERIC_CODECS.get(type);
        return map != null && map.containsKey(HashUtil.arrayIdentityWrapper(genericTypes));
    }

    /**
     * Retrieves codec for the specified type
     *
     * @param type the class type
     * @return the registered codec, or null if not found
     */
    @Nullable
    public static <T> CombinationCodec<T> get(Class<T> type) {
        if (type.isPrimitive()) return null;
        CombinationCodec<?> codec;
        if (type.isEnum()) {
            codec = ENUM_CACHE.getCache(type);
        } else if (type.isArray() && !type.componentType().isPrimitive()) {
            codec = ARRAY_CACHE.getCacheRecursion(type);
        } else {
            codec = CODECS.get(type);
        }
        return (CombinationCodec<T>) codec;
    }

    /**
     * Retrieves generic codec for the specified type with specific generic parameters
     *
     * @param type         the raw class type
     * @param genericTypes the generic type parameters
     * @return the registered codec, or null if not found
     */
    @Nullable
    public static <T> CombinationCodec<T> get(Class<?> type, Class<?>... genericTypes) {
        var map = GENERIC_CODECS.get(type);
        if (map == null) return null;
        return (CombinationCodec<T>) map.get(HashUtil.arrayIdentityWrapper(genericTypes));
    }

    public static <T> CombinationCodec<T> register(Class<T> type, ByteStreamEncoder<T> streamWriter, ByteStreamDecoder<T> streamReader, DataEncoder<T> dataWriter, DataDecoder<T> dataReader) {
        var codec = new CombinationCodec<>(streamWriter, streamReader, dataWriter, dataReader);
        synchronized (CODECS) {
            CODECS.put(type, codec);
        }
        return codec;
    }

    public static <T> CombinationCodec<T> register(Class<T> type, ByteStreamCodec<T> streamCodec, DataCodec<T> dataCodec) {
        return register(type, streamCodec, streamCodec, dataCodec, dataCodec);
    }

    public static <T> CombinationCodec<T> register(Class<T> type, ByteStreamCodec<T> streamCodec, Codec<T> codec) {
        return register(type, streamCodec, DataCodec.of(codec));
    }

    public static <T> CombinationCodec<T> register(Class<T> type, Registry<T> registry) {
        return register(type, StreamCodecs.of(registry), DataCodecs.of(registry));
    }

    public static <T> CombinationCodec<T> register(Class<?> type, ByteStreamEncoder<T> streamWriter, ByteStreamDecoder<T> streamReader, DataEncoder<T> dataWriter, DataDecoder<T> dataReader, Class<?>... genericTypes) {
        var codec = new CombinationCodec<>(streamWriter, streamReader, dataWriter, dataReader);
        synchronized (GENERIC_CODECS) {
            GENERIC_CODECS.computeIfAbsent(type, k -> new HashMap<>()).put(HashUtil.arrayIdentityWrapper(genericTypes), codec);
        }
        return codec;
    }

    public static <T> CombinationCodec<T> register(Class<T> type, ByteStreamCodec<T> streamCodec, DataCodec<T> dataCodec, Class<?>... genericTypes) {
        return register(type, streamCodec, streamCodec, dataCodec, dataCodec, genericTypes);
    }

    public final static CombinationCodec<Data> DATA_CODEC = register(Data.class, Data.BYTE_STREAM_CODEC, Data.DATA_CODEC);

    public final static CombinationCodec<MapData> MAP_DATA_CODEC = register(MapData.class, MapData.BYTE_STREAM_CODEC, MapData.DATA_CODEC);

    public final static CombinationCodec<boolean[]> BOOLEANS_CODEC = register(boolean[].class, ByteStreamCodec.BOOLEANS_CODEC, DataCodec.BOOLEANS_CODEC);
    public final static CombinationCodec<byte[]> BYTES_CODEC = register(byte[].class, ByteStreamCodec.BYTES_CODEC, DataCodec.BYTES_CODEC);
    public final static CombinationCodec<int[]> INTS_CODEC = register(int[].class, ByteStreamCodec.INTS_CODEC, DataCodec.INTS_CODEC);
    public final static CombinationCodec<long[]> LONGS_CODEC = register(long[].class, ByteStreamCodec.LONGS_CODEC, DataCodec.LONGS_CODEC);

    public final static CombinationCodec<Boolean> BOOLEAN_CODEC = register(Boolean.class, ByteStreamCodec.BOOLEAN_CODEC, DataCodec.BOOLEAN_CODEC);

    public final static CombinationCodec<Byte> BYTE_CODEC = register(Byte.class, ByteStreamCodec.BYTE_CODEC, DataCodec.BYTE_CODEC);

    public final static CombinationCodec<Short> SHORT_CODEC = register(Short.class, ByteStreamCodec.SHORT_CODEC, DataCodec.SHORT_CODEC);

    public final static CombinationCodec<Integer> INT_CODEC = register(Integer.class, ByteStreamCodec.INT_CODEC, DataCodec.INT_CODEC);

    public final static CombinationCodec<Long> LONG_CODEC = register(Long.class, ByteStreamCodec.LONG_CODEC, DataCodec.LONG_CODEC);

    public final static CombinationCodec<Float> FLOAT_CODEC = register(Float.class, ByteStreamCodec.FLOAT_CODEC, DataCodec.FLOAT_CODEC);

    public final static CombinationCodec<Double> DOUBLE_CODEC = register(Double.class, ByteStreamCodec.DOUBLE_CODEC, DataCodec.DOUBLE_CODEC);

    public final static CombinationCodec<Character> CHAR_CODEC = register(Character.class, ByteStreamCodec.CHAR_CODEC, DataCodec.CHAR_CODEC);

    public final static CombinationCodec<String> STRING_CODEC = register(String.class, ByteStreamCodec.STRING_CODEC, DataCodec.STRING_CODEC);

    public final static CombinationCodec<UUID> UUID_CODEC = register(UUID.class, ByteStreamCodec.UUID_CODEC, DataCodec.UUID_CODEC);

    public final static CombinationCodec<BigInteger> BIG_INTEGER_CODEC = register(BigInteger.class, ByteStreamCodec.BIG_INTEGER_CODEC, DataCodec.BIG_INTEGER_CODEC);

    public final static CombinationCodec<Item> ITEM_CODEC = register(Item.class, BuiltInRegistries.ITEM);
    public final static CombinationCodec<Block> BLOCK_CODEC = register(Block.class, BuiltInRegistries.BLOCK);
    public final static CombinationCodec<Fluid> FLUID_CODEC = register(Fluid.class, BuiltInRegistries.FLUID);

    public final static CombinationCodec<ResourceLocation> RESOURCE_LOCATION_CODEC = register(ResourceLocation.class, StreamCodecs.RESOURCE_LOCATION_CODEC, DataCodecs.RESOURCE_LOCATION_CODEC);

    public final static CombinationCodec<BlockPos> BLOCK_POS_CODEC = register(BlockPos.class, StreamCodecs.BLOCK_POS_CODEC, DataCodecs.BLOCK_POS_CODEC);

    public final static CombinationCodec<CompoundTag> COMPOUND_TAG_CODEC = register(CompoundTag.class, StreamCodecs.COMPOUND_TAG_CODEC, DataCodecs.COMPOUND_TAG_CODEC);

    public final static CombinationCodec<ItemStack> ITEM_STACK_CODEC = register(ItemStack.class, StreamCodecs.ITEM_STACK_CODEC, DataCodecs.ITEM_STACK_CODEC);
    public final static CombinationCodec<FluidStack> FLUID_STACK_CODEC = register(FluidStack.class, StreamCodecs.FLUID_STACK_CODEC, DataCodecs.FLUID_STACK_CODEC);

    public final static CombinationCodec<Component> COMPONENT_CODEC = register(Component.class, StreamCodecs.COMPONENT_CODEC, DataCodecs.COMPONENT_CODEC);

    public static void init() {}
}
