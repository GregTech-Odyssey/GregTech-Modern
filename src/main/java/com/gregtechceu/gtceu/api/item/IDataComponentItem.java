package com.gregtechceu.gtceu.api.item;

import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public interface IDataComponentItem {

    ItemDataComponentMap gtceu$getDataComponents();

    static ItemDataComponentMap getDataComponents(ItemStack stack) {
        return ((IDataComponentItem) (Object) stack).gtceu$getDataComponents();
    }

    static <T, NBT extends Tag> T get(ItemStack stack, ItemDataComponentKey<T, NBT> dataKey) {
        if (!stack.hasTag()) return null;
        return ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
    }

    static <T, NBT extends Tag> T getOrDefault(ItemStack stack, ItemDataComponentKey<T, NBT> dataKey, T defaultValue) {
        if (!stack.hasTag()) return defaultValue;
        return ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().getOrDefault(dataKey, defaultValue);
    }

    static <T, NBT extends Tag> T getOrSet(ItemStack stack, ItemDataComponentKey<T, NBT> dataKey, Function<ItemDataComponentKey<T, NBT>, T> defaultValueFunction) {
        return ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().getOrSet(dataKey, defaultValueFunction);
    }

    static <T, NBT extends Tag> T set(ItemStack stack, ItemDataComponentKey<T, NBT> dataKey, T value) {
        return ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().set(dataKey, value);
    }

    static <T, NBT extends Tag> void remove(ItemStack stack, ItemDataComponentKey<T, NBT> dataKey) {
        ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().remove(dataKey);
    }

    static <NBT extends Tag> boolean getBoolean(ItemStack stack, ItemDataComponentKey<Boolean, NBT> dataKey) {
        if (!stack.hasTag()) return false;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data != null && data;
    }

    static <NBT extends Tag> byte getByte(ItemStack stack, ItemDataComponentKey<Byte, NBT> dataKey) {
        if (!stack.hasTag()) return 0;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data == null ? 0 : data;
    }

    static <NBT extends Tag> short getShort(ItemStack stack, ItemDataComponentKey<Short, NBT> dataKey) {
        if (!stack.hasTag()) return 0;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data == null ? 0 : data;
    }

    static <NBT extends Tag> char getChar(ItemStack stack, ItemDataComponentKey<Character, NBT> dataKey) {
        if (!stack.hasTag()) return 0;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data == null ? 0 : data;
    }

    static <NBT extends Tag> int getInt(ItemStack stack, ItemDataComponentKey<Integer, NBT> dataKey) {
        if (!stack.hasTag()) return 0;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data == null ? 0 : data;
    }

    static <NBT extends Tag> long getLong(ItemStack stack, ItemDataComponentKey<Long, NBT> dataKey) {
        if (!stack.hasTag()) return 0;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data == null ? 0 : data;
    }

    static <NBT extends Tag> double getDouble(ItemStack stack, ItemDataComponentKey<Double, NBT> dataKey) {
        if (!stack.hasTag()) return 0;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data == null ? 0 : data;
    }

    static <NBT extends Tag> float getFloat(ItemStack stack, ItemDataComponentKey<Float, NBT> dataKey) {
        if (!stack.hasTag()) return 0;
        var data = ((IDataComponentItem) (Object) stack).gtceu$getDataComponents().get(dataKey);
        return data == null ? 0 : data;
    }
}
