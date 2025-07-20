package com.gregtechceu.gtceu.utils.cache;

import com.gregtechceu.gtceu.utils.GTTransferUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unchecked")
public class FluidHandlerDirectionCache extends DirectionCache<LazyOptional<IFluidHandler>> {

    public @NotNull LazyOptional<IFluidHandler> getAdjacentFluidHandler(Level level, BlockPos pos, Direction facing) {
        var cache = getCache(facing);
        if (cache == null) {
            var handler = GTTransferUtils.getAdjacentFluidHandler(level, pos, facing);
            handler.ifPresent(adj -> {
                handler.addListener(o -> remove(facing));
                setCache(facing, handler);
            });
            return handler;
        }
        return (LazyOptional<IFluidHandler>) cache;
    }

    public boolean hasAdjacentFluidHandler(Level level, BlockPos pos, Direction facing) {
        return getAdjacentFluidHandler(level, pos, facing).isPresent();
    }
}
