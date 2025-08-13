package com.gregtechceu.gtceu.common.pipelike.optical;

import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalDataAccessHatch;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpticalRoutePath implements IRoutePath<IOpticalComputationProvider> {

    private final OpticalPipeBlockEntity targetPipe;
    private final Direction targetFacing;
    private final int distance;

    public OpticalRoutePath(OpticalPipeBlockEntity targetPipe, Direction targetFacing, int distance) {
        this.targetPipe = targetPipe;
        this.targetFacing = targetFacing;
        this.distance = distance;
    }

    @Nullable
    public IOpticalDataAccessHatch getDataHatch() {
        IDataAccessHatch dataAccessHatch = getTargetCapability(GTCapability.CAPABILITY_DATA_ACCESS);
        return dataAccessHatch instanceof IOpticalDataAccessHatch opticalHatch ? opticalHatch : null;
    }

    @Nullable
    public IOpticalComputationHatch getComputationHatch() {
        IOpticalComputationProvider provider = getTargetCapability(GTCapability.CAPABILITY_COMPUTATION_PROVIDER);
        return provider instanceof IOpticalComputationHatch opticalHatch ? opticalHatch : null;
    }

    @Nullable
    public <I> I getTargetCapability(Capability<I> capability) {
        BlockEntity blockEntity = targetPipe.getNeighbor(targetFacing);
        return blockEntity == null ? null :
                blockEntity.getCapability(capability, targetFacing.getOpposite()).orElse(null);
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
        if (blockEntity != null) {
            return blockEntity.getCapability(GTCapability.CAPABILITY_COMPUTATION_PROVIDER, targetFacing.getOpposite()).orElse(null);
        }
        return null;
    }

    public @NotNull Direction getTargetFacing() {
        return this.targetFacing;
    }

    public int getDistance() {
        return this.distance;
    }
}
