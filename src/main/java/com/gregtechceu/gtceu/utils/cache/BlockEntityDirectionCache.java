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

import java.lang.ref.WeakReference;

public class BlockEntityDirectionCache extends DirectionCache<WeakReference<BlockEntity>> {

    public static BlockEntityDirectionCache create() {
        return new BlockEntityDirectionCache();
    }

    public BlockEntity getAdjacentBlockEntity(Level level, BlockPos pos, Direction facing) {
        var ref = getOrSet(facing, () -> {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(facing));
            if (blockEntity != null) {
                return new WeakReference<>(blockEntity);
            }
            return null;
        });
        if (ref == null) return null;
        var be = ref.get();
        if (be == null) {
            remove(facing);
            return null;
        }
        return be;
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
