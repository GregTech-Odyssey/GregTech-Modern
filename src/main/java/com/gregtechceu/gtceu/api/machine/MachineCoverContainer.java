package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.cache.BlockEntityDirectionCache;

import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.ReadOnlyManaged;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.IItemHandlerModifiable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MachineCoverContainer implements ICoverable, IEnhancedManaged {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    private final ManagedFieldHolder managedFieldHolder = MetaMachine.getManagedFieldHolder(getClass());
    private final MetaMachine machine;
    @DescSynced
    @Persisted
    @UpdateListener(methodName = "onCoverSet")
    @ReadOnlyManaged(onDirtyMethod = "onCoverDirty", serializeMethod = "serializeCoverUid", deserializeMethod = "deserializeCoverUid")
    private CoverBehavior up;
    @DescSynced
    @Persisted
    @UpdateListener(methodName = "onCoverSet")
    @ReadOnlyManaged(onDirtyMethod = "onCoverDirty", serializeMethod = "serializeCoverUid", deserializeMethod = "deserializeCoverUid")
    private CoverBehavior down;
    @DescSynced
    @Persisted
    @UpdateListener(methodName = "onCoverSet")
    @ReadOnlyManaged(onDirtyMethod = "onCoverDirty", serializeMethod = "serializeCoverUid", deserializeMethod = "deserializeCoverUid")
    private CoverBehavior north;
    @DescSynced
    @Persisted
    @UpdateListener(methodName = "onCoverSet")
    @ReadOnlyManaged(onDirtyMethod = "onCoverDirty", serializeMethod = "serializeCoverUid", deserializeMethod = "deserializeCoverUid")
    private CoverBehavior south;
    @DescSynced
    @Persisted
    @UpdateListener(methodName = "onCoverSet")
    @ReadOnlyManaged(onDirtyMethod = "onCoverDirty", serializeMethod = "serializeCoverUid", deserializeMethod = "deserializeCoverUid")
    private CoverBehavior west;
    @DescSynced
    @Persisted
    @UpdateListener(methodName = "onCoverSet")
    @ReadOnlyManaged(onDirtyMethod = "onCoverDirty", serializeMethod = "serializeCoverUid", deserializeMethod = "deserializeCoverUid")
    private CoverBehavior east;

    public MachineCoverContainer(MetaMachine machine) {
        this.machine = machine;
    }

    @Override
    public final ManagedFieldHolder getFieldHolder() {
        return managedFieldHolder;
    }

    @SuppressWarnings("unused")
    private void onCoverSet(CoverBehavior newValue, CoverBehavior oldValue) {
        if (newValue != oldValue && (newValue == null || oldValue == null)) {
            scheduleRenderUpdate();
        }
    }

    @Override
    public void onChanged() {
        machine.onChanged();
    }

    @Override
    public BlockEntityDirectionCache getBlockEntityDirectionCache() {
        return machine.blockEntityDirectionCache;
    }

    @Override
    public BlockEntity getNeighbor(Direction facing) {
        return machine.getNeighbor(facing);
    }

    @Override
    public BlockEntity holder() {
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
    public int getOffsetTimer() {
        return machine.getOffsetTimer();
    }

    @Override
    public void notifyBlockUpdate() {
        machine.notifyBlockUpdate();
    }

    @Override
    public void scheduleRenderUpdate() {
        machine.scheduleRenderUpdate();
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
        List<VoxelShape> collisionList = new ObjectArrayList<>();
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

    @Nullable
    @Override
    public TickableSubscription subscribeServerTick(Runnable runnable) {
        return machine.subscribeServerTick(runnable);
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
    public void setCoverAtSide(@Nullable CoverBehavior coverBehavior, Direction side) {
        switch (side) {
            case UP -> up = coverBehavior;
            case SOUTH -> south = coverBehavior;
            case WEST -> west = coverBehavior;
            case DOWN -> down = coverBehavior;
            case EAST -> east = coverBehavior;
            case NORTH -> north = coverBehavior;
        }
        machine.onCoverUpdate(coverBehavior, side);
        if (coverBehavior != null) {
            coverBehavior.getSyncStorage().markAllDirty();
        }
    }

    @Override
    public IItemHandlerModifiable getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return machine.getItemHandlerCap(side, useCoverCapability);
    }

    @Override
    public IFluidHandlerModifiable getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return machine.getFluidHandlerCap(side, useCoverCapability);
    }

    @SuppressWarnings("unused")
    private boolean onCoverDirty(CoverBehavior coverBehavior) {
        if (coverBehavior != null) {
            for (IRef ref : coverBehavior.getSyncStorage().getNonLazyFields()) {
                ref.update();
            }
            return coverBehavior.getSyncStorage().hasDirtySyncFields() || coverBehavior.getSyncStorage().hasDirtyPersistedFields();
        }
        return false;
    }

    @SuppressWarnings("unused")
    private CompoundTag serializeCoverUid(CoverBehavior coverBehavior) {
        var uid = new CompoundTag();
        uid.putString("id", GTRegistries.COVERS.getKey(coverBehavior.coverDefinition).toString());
        uid.putInt("side", coverBehavior.attachedSide.ordinal());
        return uid;
    }

    @SuppressWarnings("unused")
    private CoverBehavior deserializeCoverUid(CompoundTag uid) {
        var definitionId = new ResourceLocation(uid.getString("id"));
        var side = GTUtil.DIRECTIONS[uid.getInt("side")];
        var definition = GTRegistries.COVERS.get(definitionId);
        if (definition != null) {
            return definition.createCoverBehavior(this, side);
        }
        GTCEu.LOGGER.error("couldn\'t find cover definition {}", definitionId);
        throw new RuntimeException();
    }

    public FieldManagedStorage getSyncStorage() {
        return this.syncStorage;
    }

    public MetaMachine getMachine() {
        return this.machine;
    }
}
