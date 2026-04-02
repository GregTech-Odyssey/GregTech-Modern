package com.tterrag.registrate.util.entry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntry<T extends Block> extends ItemProviderEntry<T> {

    public BlockEntry(ResourceKey<T> key) {
        super(key);
    }

    public BlockState getDefaultState() {
        return value.defaultBlockState();
    }

    public boolean has(BlockState state) {
        return state.getBlock() == value;
    }

    public static <T extends Block> BlockEntry<T> cast(RegistryEntry<T> entry) {
        return RegistryEntry.cast(BlockEntry.class, entry);
    }
}
