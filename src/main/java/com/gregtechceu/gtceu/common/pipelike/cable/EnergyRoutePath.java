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

    public EnergyRoutePath(BlockPos targetPipePos, Direction targetFacing, CableBlockEntity[] path, int distance, long maxLoss) {
        this.targetPipePos = targetPipePos;
        this.targetFacing = targetFacing;
        this.path = path;
        this.distance = distance;
        this.maxLoss = maxLoss;
    }

    @Nullable
    public IEnergyContainer getHandler(Level world) {
        if (pipeBlockEntity == null) {
            pipeBlockEntity = world.getBlockEntity(getTargetPipePos()) instanceof PipeBlockEntity<?, ?> entity ? entity : null;
        } else {
            return GTCapabilityHelper.getEnergyContainer(pipeBlockEntity.getNeighborBlockEntity(targetFacing), targetFacing.getOpposite());
        }
        return GTCapabilityHelper.getEnergyContainer(world.getBlockEntity(getTargetPipePos().relative(targetFacing)), targetFacing.getOpposite());
    }

    public @NotNull BlockPos getTargetPipePos() {
        return this.targetPipePos;
    }

    public @NotNull Direction getTargetFacing() {
        return this.targetFacing;
    }
}
