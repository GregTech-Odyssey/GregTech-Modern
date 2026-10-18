package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.utils.cache.BlockEntityDirectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.annotations.Access;
import com.gto.datasynclib.annotations.Codec;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class MachineCoverContainer implements ICoverable {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);

    @Getter
    private final MetaMachine machine;

    @SuppressWarnings("all")
    @SaveToDisk
    @SyncToClient(listener = "onCoverSet")
    @Access(instanceAsValue = true)
    @Codec(writeToValue = "serializeCoverData", readFromValue = "deserializeCoverData", writeToBuffer = "serializeCoverBuffer", readFromBuffer = "deserializeCoverBuffer")
    private CoverBehavior up, down, north, south, west, east;
    @Getter
    private final CoverBehavior[] allCovers = new CoverBehavior[6];
    @Getter
    @Setter
    private CoverBehavior[] covers = EMPTY;
    @Getter
    @Setter
    private Direction[] coverSides = EMPTY_DIRECTION;

    public MachineCoverContainer(MetaMachine machine) {
        this.machine = machine;
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
    public void invalidateCapabilities() {
        machine.clearDirectionCache();
    }

    @Override
    public void notifyNeighborsUpdate() {
        machine.notifyNeighborsUpdate();
    }

    @Override
    public void scheduleNeighborShapeUpdate() {
        machine.scheduleNeighborShapeUpdate();
    }

    @Override
    public boolean isInValid() {
        return machine.isRemoved();
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
        return allCovers[side.ordinal()];
    }

    @Override
    public CoverBehavior getInternalCoverAtSide(Direction side) {
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
        allCovers[side.ordinal()] = coverBehavior;
        loadAllCover();
    }

    @Override
    public void setCoverAtSide(@Nullable CoverBehavior coverBehavior, Direction side) {
        setCoverAtSideinternal(coverBehavior, side);
        machine.onCoverUpdate(coverBehavior, side);
        getFieldDataManager().markAsChanged();
    }

    @Override
    public IKeyHandler<AEItemKey> getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return machine.getItemHandlerCap(side, useCoverCapability);
    }

    @Override
    public IKeyHandler<AEFluidKey> getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return machine.getFluidHandlerCap(side, useCoverCapability);
    }

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }

    @Override
    public @Nullable IFieldDataHolder getParentHolder() {
        return machine;
    }
}
