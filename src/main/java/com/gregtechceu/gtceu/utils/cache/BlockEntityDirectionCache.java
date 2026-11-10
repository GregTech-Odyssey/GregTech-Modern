package com.gregtechceu.gtceu.utils.cache;

import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageAccess;
import org.jetbrains.annotations.Nullable;

public class BlockEntityDirectionCache extends DirectionCache<BlockEntity> {

    private final KeyTarget[] targets = new KeyTarget[6];

    public static BlockEntityDirectionCache create() {
        return new BlockEntityDirectionCache();
    }

    public final BlockEntity getAdjacentBlockEntity(Level level, BlockPos pos, Direction direction) {
        var cache = getCache(direction);
        if (cache == null) {
            return set(level, pos, direction);
        } else {
            if (cache == NULL) return null;
            var blockEntity = (BlockEntity) cache;
            if (blockEntity.isRemoved()) {
                return set(level, pos, direction);
            }
            return blockEntity;
        }
    }

    private BlockEntity set(Level level, BlockPos pos, Direction direction) {
        var blockEntity = level.getBlockEntity(pos.relative(direction));
        setCache(direction, blockEntity == null ? NULL : blockEntity);
        return blockEntity;
    }

    public final boolean hasAdjacentTarget(Level level, BlockPos pos, Direction facing, AEKeyType type, StorageAccess access) {
        var blockEntity = getAdjacentBlockEntity(level, pos, facing);
        return blockEntity != null && target(facing).has(blockEntity, facing.getOpposite(), type, access);
    }

    public final @Nullable IKeyHandler<?> getAdjacentKeyHandler(Level level, BlockPos pos, Direction facing, AEKeyType type, StorageAccess access) {
        var blockEntity = getAdjacentBlockEntity(level, pos, facing);
        return blockEntity == null ? null : target(facing).find(blockEntity, facing.getOpposite(), type, access);
    }

    public final @Nullable IKeyHandler<?> getAdjacentKeyHandler(BlockEntity neighbour, Direction facing, AEKeyType type, StorageAccess access) {
        return target(facing).find(neighbour, facing.getOpposite(), type, access);
    }

    private KeyTarget target(Direction facing) {
        int i = facing.ordinal();
        var t = targets[i];
        if (t == null) targets[i] = t = new KeyTarget();
        return t;
    }
}
