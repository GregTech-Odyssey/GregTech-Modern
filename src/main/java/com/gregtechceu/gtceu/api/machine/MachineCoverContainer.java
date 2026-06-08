package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.utils.cache.BlockEntityDirectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.annotations.Access;
import com.gto.datasynclib.annotations.Codec;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class MachineCoverContainer implements ICoverable {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);

    @Getter
    private final MetaMachine machine;

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

    public MachineCoverContainer(MetaMachine machine) {
        this.machine = machine;
    }

    @SuppressWarnings("unused")
    private void onCoverSet(CoverBehavior newValue, CoverBehavior oldValue) {
        if (newValue != oldValue && (newValue == null || oldValue == null)) {
            scheduleUpdate(LogicalSide.CLIENT);
        }
    }

    @Override
    public void onChanged() {
        machine.onChanged();
    }

    @Override
    public BlockEntityDirectionCache getBlockEntityDirectionCache() {
        return machine.holder.blockEntityDirectionCache;
    }

    @Override
    public BlockEntity getNeighbor(Direction facing) {
        return machine.holder.getNeighborBlockEntity(facing);
    }

    @Override
    public GTBlockEntity holder() {
        return machine.holder;
    }

    @Override
    public Level getLevel() {
        return machine.getLevel();
    }

    @Override
    public BlockPos getPos() {
        return machine.getPos();
    }

    @Override
    public void notifyBlockUpdate() {
        machine.notifyBlockUpdate();
    }

    @Override
    public void scheduleNeighborShapeUpdate() {
        machine.scheduleNeighborShapeUpdate();
    }

    @Override
    public boolean isInValid() {
        return machine.isInValid();
    }

    @Override
    public boolean canPlaceCoverOnSide(CoverDefinition definition, Direction side) {
        List<VoxelShape> collisionList = new ArrayList<>();
        machine.addCollisionBoundingBox(collisionList);
        // noinspection RedundantIfStatement
        if (ICoverable.doesCoverCollide(side, collisionList, getCoverPlateThickness())) {
            // cover collision box overlaps with meta tile entity collision box
            return false;
        }
        return true;
    }

    @Override
    public double getCoverPlateThickness() {
        return 0;
    }

    @Override
    public Direction getFrontFacing() {
        return machine.getFrontFacing();
    }

    @Override
    public boolean shouldRenderBackSide() {
        return !machine.getBlockState().canOcclude();
    }

    @Override
    public GTBlockEntity getHolder() {
        return machine.holder;
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
        setCoverAtSideinternal(coverBehavior, side);
        machine.onCoverUpdate(coverBehavior, side);
        getFieldDataManager().markAsChanged();
    }

    @Override
    public ICustomItemStackHandler getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return machine.getItemHandlerCap(side, useCoverCapability);
    }

    @Override
    public ICustomFluidStackHandler getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return machine.getFluidHandlerCap(side, useCoverCapability);
    }

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }

    @Override
    public void scheduleUpdate(LogicalSide side) {
        machine.scheduleUpdate(side);
    }
}
