package com.tterrag.registrate.util.entry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.ForgeRegistries;

import com.tterrag.registrate.AbstractRegistrate;

import java.util.Optional;

import javax.annotation.Nullable;

public class FluidEntry<T extends ForgeFlowingFluid> extends RegistryEntry<T> {

    private final @Nullable BlockEntry<? extends Block> block;

    public FluidEntry(AbstractRegistrate<?> owner, ResourceKey<T> key) {
        super(key);
        BlockEntry<? extends Block> block = null;
        try {
            block = BlockEntry.cast(getSibling(owner, ForgeRegistries.BLOCKS));
        } catch (IllegalArgumentException e) {} // TODO add way to get entry optionally
        this.block = block;
    }

    @Override
    public <R> boolean is(R entry) {
        return value.isSame((Fluid) entry);
    }

    @SuppressWarnings("unchecked")
    public <S extends ForgeFlowingFluid> S getSource() {
        return (S) value.getSource();
    }

    public FluidType getType() {
        return value.getFluidType();
    }

    @SuppressWarnings({ "unchecked", "null" })
    public <B extends Block> Optional<B> getBlock() {
        return (Optional<B>) Optional.ofNullable(block).map(RegistryEntry::get);
    }

    @SuppressWarnings({ "unchecked", "null" })
    public <I extends Item> Optional<I> getBucket() {
        return Optional.ofNullable((I) value.getBucket());
    }
}
