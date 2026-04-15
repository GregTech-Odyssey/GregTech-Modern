package com.gto.registrate.util.entry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

import javax.annotation.Nullable;

public class BlockEntityEntry<T extends BlockEntity> extends RegistryEntry<BlockEntityType<T>> {

    public BlockEntityEntry(ResourceKey<BlockEntityType<T>> key) {
        super(key);
    }

    /**
     * Create a "default" instance of this {@link BlockEntity} via the {@link BlockEntityType}.
     * 
     * @return The instance
     */
    public T create(BlockPos pos, BlockState state) {
        return value.create(pos, state);
    }

    /**
     * Check that the given {@link BlockEntity} is an instance of this type.
     * 
     * @param t
     *          The {@link BlockEntity} instance
     * @return {@code true} if the type matches, {@code false} otherwise.
     */
    public boolean is(@Nullable BlockEntity t) {
        return t != null && t.getType() == value;
    }

    /**
     * Get an instance of this {@link BlockEntity} from the world.
     * 
     * @param world
     *              The world to look for the instance in
     * @param pos
     *              The position of the instance
     * @return An {@link Optional} containing the instance, if it exists and matches this type. Otherwise,
     *         {@link Optional#empty()}.
     */
    public Optional<T> get(BlockGetter world, BlockPos pos) {
        return Optional.ofNullable(getNullable(world, pos));
    }

    /**
     * Get an instance of this {@link BlockEntity} from the world.
     * 
     * @param world
     *              The world to look for the instance in
     * @param pos
     *              The position of the instance
     * @return The instance, if it exists and matches this type. Otherwise, {@code null}.
     */
    @SuppressWarnings("unchecked")
    public @Nullable T getNullable(BlockGetter world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return is(be) ? (T) be : null;
    }

    public static <T extends BlockEntity> BlockEntityEntry<T> cast(RegistryEntry<BlockEntityType<T>> entry) {
        return RegistryEntry.cast(BlockEntityEntry.class, entry);
    }
}
