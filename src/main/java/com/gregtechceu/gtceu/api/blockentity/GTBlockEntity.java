package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.SCPacketSBlockEntitySync;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public abstract class GTBlockEntity extends BlockEntity implements ISync, ITickSubscription {

    public final int offset = GTValues.RNG.nextInt(20);
    public volatile boolean remove = false;
    public final BooleanSupplier isRemove = () -> remove;
    public int tickDelay = 0;

    protected LevelChunk chunk;

    private TickableSubscription autoSyncSubscription;

    public boolean sync = true;

    public boolean observe;

    private boolean changed;

    public GTBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public abstract ICoverable getCoverContainer();

    @Override
    public boolean isRemoved() {
        return this.remove;
    }

    @Nullable
    public <T> T getGTCapability(@NotNull Class<T> cap, @Nullable Direction side) {
        return null;
    }

    public void observe() {
        observe = true;
        sync = true;
        setChanged();
    }

    public boolean needSync() {
        if (sync) {
            sync = false;
            return true;
        }
        return false;
    }

    public int getOffsetTimer() {
        if (level == null) return offset;
        var server = level.getServer();
        if (server != null) return server.getTickCount() + offset;
        return GTValues.CLIENT_TIME + offset;
    }

    public @Nullable LevelChunk getChunk() {
        if (remove) return null;
        if (chunk != null) return chunk;
        if (level != null) return chunk = level.getChunkAt(worldPosition);
        return null;
    }

    public void notifyBlockUpdate() {
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public void scheduleRenderUpdate() {
        var level = this.level;
        if (level != null) {
            if (level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
            } else {
                level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
            }
        }
    }

    @Override
    public GTBlockEntity getHolder() {
        return this;
    }

    @Override
    public boolean triggerEvent(int id, int para) {
        if (id == 1) {
            if (level != null && level.isClientSide) {
                scheduleRenderUpdate();
            }
            return true;
        }
        return false;
    }

    @Override
    public void setChanged() {
        if (changed) return;
        if (level instanceof ServerLevel serverLevel) {
            changed = true;
            TaskHandler.enqueueTask(serverLevel, () -> {
                serverLevel.blockEntityChanged(worldPosition);
                changed = false;
            }, 0);
        }
    }

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);
        chunk = null;
    }

    @Override
    public void setRemoved() {
        this.remove = true;
        autoSyncSubscription = ITickSubscription.unsubscribe(autoSyncSubscription);
        super.setRemoved();
        chunk = null;
    }

    @Override
    public void clearRemoved() {
        changed = false;
        this.remove = false;
        chunk = null;
        super.clearRemoved();
        if (level instanceof ServerLevel serverLevel) {
            tickDelay = offset;
            autoSyncSubscription = subscribeAsyncTick(autoSyncSubscription, this::autoSync, 5);
            TaskHandler.enqueueTask(serverLevel, () -> tickDelay = 0, 1);
        }
    }

    protected int periodID = offset;

    protected void autoSync() {
        asyncTick(periodID++);
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        if (getFieldDataManager().hasSyncField(LogicalSide.SERVER)) {
            tag.putByteArray("field_sync", getFieldDataManager().writeToNetworkBuffer(LogicalSide.SERVER, true));
        }
        return tag;
    }

    @Override
    public final void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.get("field_sync") instanceof ByteArrayTag byteArrayTag) {
            getFieldDataManager().readFromNetworkBuffer(LogicalSide.CLIENT, byteArrayTag.getAsByteArray());
        } else {
            loadCustomPersistedData(tag);
        }
        if (tag.get("field_save") instanceof ByteArrayTag byteArrayTag) {
            getFieldDataManager().readFromData((MapData) Data.readData(byteArrayTag.getAsByteArray()), tag.getInt("field_data_dataVersion"));
        } else {
            getFieldDataManager().readFromData((MapData) DataCodecs.COMPOUND_TAG_CODEC.encode(tag), -1);
        }
    }

    @Override
    protected final void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("field_data_dataVersion", 0);
        tag.putByteArray("field_save", getFieldDataManager().writeToData().writeToBytes());
        saveCustomPersistedData(tag, false);
    }

    public void loadCustomPersistedData(CompoundTag tag) {}

    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {}

    public void asyncTick(long periodID) {
        if (remove) return;
        if (needSync() || periodID % 40 == 0) {
            var server = GTCEu.getMinecraftServer();
            if (server != null) {
                if (getFieldDataManager().updateFieldDirtyFlags(LogicalSide.SERVER, true)) {
                    var p = SCPacketSBlockEntitySync.of(this, false);
                    if (remove) return;
                    server.execute(() -> {
                        if (remove) return;
                        GTNetwork.NETWORK.sendToTrackingChunk(p, getChunk());
                    });
                }
            }
        }
    }

    @Override
    public void scheduleUpdate(LogicalSide side) {
        if (side.isClient()) scheduleRenderUpdate();
    }
}
