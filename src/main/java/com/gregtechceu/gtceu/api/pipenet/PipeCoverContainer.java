package com.gregtechceu.gtceu.api.pipenet;

import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.utils.cache.BlockEntityDirectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.annotations.Access;
import com.gto.datasynclib.annotations.Codec;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import org.jetbrains.annotations.Nullable;

public final class PipeCoverContainer implements ICoverable {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);

    private final PipeBlockEntity<?, ?> pipeTile;

    @SaveToDisk
    @SyncToClient(listener = "onCoverSet")
    @Access(createInstance = true)
    @Codec(writeToData = "serializeCoverData", readFromData = "deserializeCoverData", writeToBuffer = "serializeCoverBuffer", readFromBuffer = "deserializeCoverBuffer")
    private CoverBehavior up;

    @SaveToDisk
    @SyncToClient(listener = "onCoverSet")
    @Access(createInstance = true)
    @Codec(writeToData = "serializeCoverData", readFromData = "deserializeCoverData", writeToBuffer = "serializeCoverBuffer", readFromBuffer = "deserializeCoverBuffer")
    private CoverBehavior down;

    @SaveToDisk
    @SyncToClient(listener = "onCoverSet")
    @Access(createInstance = true)
    @Codec(writeToData = "serializeCoverData", readFromData = "deserializeCoverData", writeToBuffer = "serializeCoverBuffer", readFromBuffer = "deserializeCoverBuffer")
    private CoverBehavior north;

    @SaveToDisk
    @SyncToClient(listener = "onCoverSet")
    @Access(createInstance = true)
    @Codec(writeToData = "serializeCoverData", readFromData = "deserializeCoverData", writeToBuffer = "serializeCoverBuffer", readFromBuffer = "deserializeCoverBuffer")
    private CoverBehavior south;

    @SaveToDisk
    @SyncToClient(listener = "onCoverSet")
    @Access(createInstance = true)
    @Codec(writeToData = "serializeCoverData", readFromData = "deserializeCoverData", writeToBuffer = "serializeCoverBuffer", readFromBuffer = "deserializeCoverBuffer")
    private CoverBehavior west;

    @SaveToDisk
    @SyncToClient(listener = "onCoverSet")
    @Access(createInstance = true)
    @Codec(writeToData = "serializeCoverData", readFromData = "deserializeCoverData", writeToBuffer = "serializeCoverBuffer", readFromBuffer = "deserializeCoverBuffer")
    private CoverBehavior east;

    public PipeCoverContainer(PipeBlockEntity<?, ?> pipeTile) {
        this.pipeTile = pipeTile;
    }

    @SuppressWarnings("unused")
    private void onCoverSet(CoverBehavior newValue, CoverBehavior oldValue) {
        if (newValue != oldValue && (newValue == null || oldValue == null)) {
            scheduleUpdate(LogicalSide.CLIENT);
        }
    }

    @Override
    public void onChanged() {
        pipeTile.setChanged();
    }

    @Override
    public BlockEntityDirectionCache getBlockEntityDirectionCache() {
        return pipeTile.blockEntityDirectionCache;
    }

    @Override
    public BlockEntity getNeighbor(Direction facing) {
        return pipeTile.getNeighborBlockEntity(facing);
    }

    @Override
    public GTBlockEntity holder() {
        return pipeTile;
    }

    @Override
    public Level getLevel() {
        return pipeTile.getLevel();
    }

    @Override
    public BlockPos getPos() {
        return pipeTile.getPipePos();
    }

    @Override
    public void notifyBlockUpdate() {
        pipeTile.notifyBlockUpdate();
    }

    @Override
    public void scheduleNeighborShapeUpdate() {
        pipeTile.scheduleNeighborShapeUpdate();
    }

    @Override
    public boolean isInValid() {
        return pipeTile.isInValid();
    }

    @Override
    public boolean canPlaceCoverOnSide(CoverDefinition definition, Direction side) {
        return getCoverAtSide(side) == null;
    }

    @Override
    public double getCoverPlateThickness() {
        float thickness = pipeTile.getPipeType().getThickness();
        // no cover plate for pipes >= 1 block thick
        if (thickness >= 1) return 0;
        // If the available space for the cover is less than the regular cover plate thickness, use that
        // need to divide by 2 because thickness is centered on the block, so the space is half on each side of the pipe
        return Math.min(1.0 / 16.0, (1.0 - thickness) / 2);
    }

    @Override
    public Direction getFrontFacing() {
        return Direction.NORTH;
    }

    @Override
    public boolean shouldRenderBackSide() {
        return true;
    }

    @Override
    public GTBlockEntity getHolder() {
        return pipeTile;
    }

    @Override
    public ICustomItemStackHandler getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (pipeTile instanceof ItemPipeBlockEntity itemPipe && getLevel() instanceof ServerLevel && itemPipe.getHandler(side, useCoverCapability) instanceof ICustomItemStackHandler itemHandlerModifiable) {
            return itemHandlerModifiable;
        } else {
            return null;
        }
    }

    @Override
    public ICustomFluidStackHandler getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (pipeTile instanceof FluidPipeBlockEntity fluidPipe && getLevel() instanceof ServerLevel && fluidPipe.getHandler(side, useCoverCapability) instanceof ICustomFluidStackHandler fluidHandlerModifiable) {
            return fluidHandlerModifiable;
        } else {
            return null;
        }
    }

    @Override
    public CoverBehavior getCoverAtSide(Direction side) {
        return switch (side) {
            case UP -> up;
            case SOUTH -> south;
            case WEST -> west;
            case DOWN -> down;
            case EAST -> east;
            case NORTH -> north;
        };
    }

    @Override
    public void setCoverAtSideinternal(@Nullable CoverBehavior coverBehavior, Direction side) {
        switch (side) {
            case UP -> up = coverBehavior;
            case SOUTH -> south = coverBehavior;
            case WEST -> west = coverBehavior;
            case DOWN -> down = coverBehavior;
            case EAST -> east = coverBehavior;
            case NORTH -> north = coverBehavior;
        }
    }

    @Override
    public void setCoverAtSide(@Nullable CoverBehavior coverBehavior, Direction side) {
        var previousCover = getCoverAtSide(side);
        setCoverAtSideinternal(coverBehavior, side);
        getFieldDataManager().markAsChanged();
        if (coverBehavior != null) {
            if (coverBehavior.canPipePassThrough()) {
                pipeTile.setConnection(side, true, false);
            }
        } else if (previousCover != null && previousCover.canPipePassThrough()) {
            pipeTile.setConnection(side, false, false);
        }
    }

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }

    @Override
    public void scheduleUpdate(LogicalSide side) {
        pipeTile.scheduleUpdate(side);
    }
}
