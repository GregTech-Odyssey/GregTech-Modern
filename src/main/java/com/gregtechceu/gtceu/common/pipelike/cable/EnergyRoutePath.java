package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnergyRoutePath implements IRoutePath<IEnergyContainer> {

    private PipeBlockEntity<?, ?> pipeBlockEntity;
    private final BlockPos targetPipePos;
    private final Direction targetFacing;
    @Getter
    private final int distance;
    @Getter
    private final CableBlockEntity[] path;
    @Getter
    private final long maxLoss;
    @Getter
    private final long minVoltage;

    public EnergyRoutePath(BlockPos targetPipePos, Direction targetFacing, CableBlockEntity[] path, int distance, long maxLoss, long minVoltage) {
        this.targetPipePos = targetPipePos;
        this.targetFacing = targetFacing;
        this.path = path;
        this.distance = distance;
        this.maxLoss = maxLoss;
        this.minVoltage = minVoltage;
    }

    @Nullable
    public IEnergyContainer getHandler(Level world) {
        var pipe = pipeBlockEntity;
        if (pipe == null) {
            pipe = pipeBlockEntity = world.getBlockEntity(getTargetPipePos()) instanceof PipeBlockEntity<?, ?> entity ? entity : null;
        }
        // a paused cable must not push energy into its neighbours
        if (pipe == null || !pipe.isWorkingEnabled()) return null;
        return GTCapabilityHelper.getEnergyContainer(pipe.getNeighborBlockEntity(targetFacing), targetFacing.getOpposite());
    }

    public @NotNull BlockPos getTargetPipePos() {
        return this.targetPipePos;
    }

    public @NotNull Direction getTargetFacing() {
        return this.targetFacing;
    }
}
