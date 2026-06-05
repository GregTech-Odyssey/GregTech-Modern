package com.gregtechceu.gtceu.common.pipelike.laser;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ILaserContainer;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;
import com.gregtechceu.gtceu.common.blockentity.LaserPipeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LaserRoutePath implements IRoutePath<ILaserContainer> {

    private final LaserPipeBlockEntity targetPipe;
    private final BlockPos targetPipePos;
    /**
     * the current face to handler
     */
    @NotNull
    private final Direction targetFacing;
    /**
     * the manhattan distance traveled during walking
     * -- GETTER --
     * the manhattan distance traveled during walking
     * 
     */
    @Getter
    private final int distance;
    @Getter
    byte connections;

    public LaserRoutePath(LaserPipeBlockEntity targetPipe, BlockPos targetPipePos, @NotNull Direction targetFacing, int distance) {
        this.targetPipe = targetPipe;
        this.targetPipePos = targetPipePos;
        this.targetFacing = targetFacing;
        this.distance = distance;
    }

    /**
     * Gets the handler if it exists
     *
     * @return the handler
     */
    @Nullable
    public ILaserContainer getHandler(Level level) {
        return GTCapabilityHelper.getLaser(targetPipe.getNeighbor(targetFacing), targetFacing.getOpposite());
    }

    public @NotNull BlockPos getTargetPipePos() {
        return this.targetPipePos;
    }

    /**
     * the current face to handler
     */
    @NotNull
    public Direction getTargetFacing() {
        return this.targetFacing;
    }
}
