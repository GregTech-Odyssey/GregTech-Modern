package com.gregtechceu.gtceu.api.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import com.gto.datasynclib.datastream.DataComponentKey;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ItemDataComponentKey<T, NBT extends Tag> extends DataComponentKey<T> {

    public final BiConsumer<NBT, T> writerCompound;
    public final Function<T, NBT> writer;
    public final Function<NBT, T> reader;
    public final boolean compound;
    public final Consumer<CompoundTag> remove;

    private ItemDataComponentKey(String name, BiConsumer<NBT, T> writerCompound, Function<NBT, T> reader, Consumer<CompoundTag> remove) {
        super(name, null);
        this.writerCompound = writerCompound;
        this.reader = reader;
        this.compound = true;
        this.remove = remove;
        this.writer = null;
    }

    private ItemDataComponentKey(String name, Function<T, NBT> writer, Function<NBT, T> reader) {
        super(name, null);
        this.writer = writer;
        this.reader = reader;
        this.compound = false;
        this.remove = null;
        this.writerCompound = null;
    }

    public static <T, NBT extends Tag> ItemDataComponentKey<T, NBT> create(String name, Function<T, NBT> writer, Function<NBT, T> reader) {
        return new ItemDataComponentKey<>(name, writer, reader);
    }

    public static <T> ItemDataComponentKey<T, CompoundTag> createCompound(String name, BiConsumer<CompoundTag, T> writer, Function<CompoundTag, T> reader, Consumer<CompoundTag> remove) {
        return new ItemDataComponentKey<>(name, writer, reader, remove);
    }
}
