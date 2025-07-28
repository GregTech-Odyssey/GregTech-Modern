package com.gregtechceu.gtceu.utils.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;

public class BlockEntityDirectionCache extends DirectionCache<BlockEntity> {

    public static BlockEntityDirectionCache create() {
        return new BlockEntityDirectionCache();
    }

    public BlockEntity getAdjacentBlockEntity(Level level, BlockPos pos, Direction direction) {
        var cache = getCache(direction);
        if (cache == null) {
            var blockEntity = level.getBlockEntity(pos.relative(direction));
            setCache(direction, blockEntity == null ? NULL : blockEntity);
            return blockEntity;
        } else {
            if (cache == NULL) return null;
            var blockEntity = (BlockEntity) cache;
            if (blockEntity.isRemoved()) {
                blockEntity = level.getBlockEntity(pos.relative(direction));
                if (blockEntity != null) {
                    setCache(direction, blockEntity);
                    return blockEntity;
                } else {
                    setCache(direction, NULL);
                    return null;
                }
            }
            return blockEntity;
        }
    }

    public @NotNull LazyOptional<IItemHandler> getAdjacentItemHandler(Level level, BlockPos pos, Direction facing) {
        var blockEntity = getAdjacentBlockEntity(level, pos, facing);
        if (blockEntity != null) {
            return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, facing.getOpposite());
        }
        return LazyOptional.empty();
    }

    public boolean hasAdjacentItemHandler(Level level, BlockPos pos, Direction facing) {
        return getAdjacentItemHandler(level, pos, facing).isPresent();
    }

    public @NotNull LazyOptional<IFluidHandler> getAdjacentFluidHandler(Level level, BlockPos pos, Direction facing) {
        var blockEntity = getAdjacentBlockEntity(level, pos, facing);
        if (blockEntity != null) {
            return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, facing.getOpposite());
        }
        return LazyOptional.empty();
    }

    public boolean hasAdjacentFluidHandler(Level level, BlockPos pos, Direction facing) {
        return getAdjacentFluidHandler(level, pos, facing).isPresent();
    }
}
