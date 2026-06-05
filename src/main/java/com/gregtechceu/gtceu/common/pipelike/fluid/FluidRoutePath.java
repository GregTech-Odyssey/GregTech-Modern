package com.gregtechceu.gtceu.common.pipelike.fluid;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;
import com.gregtechceu.gtceu.utils.FacingPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class FluidRoutePath implements IRoutePath<IFluidHandler> {

    @Getter
    private final FluidPipeBlockEntity targetPipe;
    @NotNull
    private final Direction targetFacing;
    @Getter
    private final int distance;
    @Getter
    private final FluidPipeProperties properties;
    private final Predicate<FluidStack> filters;

    public FluidRoutePath(FluidPipeBlockEntity targetPipe, @NotNull Direction facing, int distance, FluidPipeProperties properties, List<Predicate<FluidStack>> filters) {
        this.targetPipe = targetPipe;
        this.targetFacing = facing;
        this.distance = distance;
        this.properties = properties;
        this.filters = stack -> {
            for (Predicate<FluidStack> filter : filters) if (!filter.test(stack)) return false;
            return true;
        };
    }

    @Override
    @NotNull
    public BlockPos getTargetPipePos() {
        return targetPipe.getPipePos();
    }

    @Override
    @Nullable
    public IFluidHandler getHandler(Level world) {
        return targetPipe.blockEntityDirectionCache.getAdjacentFluidHandler(world, getTargetPipePos(), targetFacing).orElse(null);
    }

    public boolean matchesFilters(FluidStack stack) {
        return filters.test(stack);
    }

    public FacingPos toFacingPos() {
        return new FacingPos(getTargetPipePos(), targetFacing);
    }

    @NotNull
    public Direction getTargetFacing() {
        return this.targetFacing;
    }
}
