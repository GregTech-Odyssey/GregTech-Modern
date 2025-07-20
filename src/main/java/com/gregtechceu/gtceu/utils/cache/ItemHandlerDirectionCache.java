package com.gregtechceu.gtceu.utils.cache;

import com.gregtechceu.gtceu.utils.GTTransferUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unchecked")
public class ItemHandlerDirectionCache extends DirectionCache<LazyOptional<IItemHandler>> {

    public @NotNull LazyOptional<IItemHandler> getAdjacentItemHandler(Level level, BlockPos pos, Direction facing) {
        var cache = getCache(facing);
        if (cache == null) {
            var handler = GTTransferUtils.getAdjacentItemHandler(level, pos, facing);
            handler.ifPresent(adj -> {
                handler.addListener(o -> remove(facing));
                setCache(facing, handler);
            });
            return handler;
        }
        return (LazyOptional<IItemHandler>) cache;
    }

    public boolean hasAdjacentItemHandler(Level level, BlockPos pos, Direction facing) {
        return getAdjacentItemHandler(level, pos, facing).isPresent();
    }
}
