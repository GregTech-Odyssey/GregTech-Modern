package com.gregtechceu.gtceu.common.pipelike.optical;

import com.gregtechceu.gtceu.api.capability.*;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OpticalRoutePath implements IRoutePath<IOpticalComputationProvider> {

    private final OpticalPipeBlockEntity targetPipe;
    private final Direction targetFacing;
    @Getter
    private final int distance;

    public OpticalRoutePath(OpticalPipeBlockEntity targetPipe, Direction targetFacing, int distance) {
        this.targetPipe = targetPipe;
        this.targetFacing = targetFacing;
        this.distance = distance;
    }

    @Nullable
    public IOpticalDataAccessHatch getDataHatch() {
        IDataAccessHatch dataAccessHatch = getTargetCapability(GTCapability.DATA_ACCESS);
        return dataAccessHatch instanceof IOpticalDataAccessHatch opticalHatch ? opticalHatch : null;
    }

    @Nullable
    public IOpticalComputationHatch getComputationHatch() {
        IOpticalComputationProvider provider = getTargetCapability(GTCapability.COMPUTATION_PROVIDER);
        return provider instanceof IOpticalComputationHatch opticalHatch ? opticalHatch : null;
    }

    @Nullable
    public <I> I getTargetCapability(Class<I> capability) {
        BlockEntity blockEntity = targetPipe.getNeighbor(targetFacing);
        return GTCapabilityHelper.getBlockEntityGTCapability(capability, blockEntity, targetFacing.getOpposite());
    }

    @Override
    @NotNull
    public BlockPos getTargetPipePos() {
        return targetPipe.getPipePos();
    }

    @Nullable
    @Override
    public IOpticalComputationProvider getHandler(Level world) {
        BlockEntity blockEntity = targetPipe.getNeighbor(targetFacing);
        return GTCapabilityHelper.getComputation(blockEntity, targetFacing.getOpposite());
    }

    public @NotNull Direction getTargetFacing() {
        return this.targetFacing;
    }
}
