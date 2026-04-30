package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.SCPacketSBlockEntitySync;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.networking.LDLNetworking;
import com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.MapData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public abstract class TickBlockEntity extends BlockEntity implements IFieldDataHolder, IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, ITickSubscription {

    public final int offset = GTValues.RNG.nextInt(20);
    public volatile boolean remove = false;
    public final BooleanSupplier isRemove = () -> remove;
    public int tickDelay = 0;

    protected LevelChunk chunk;

    private TickableSubscription autoSyncSubscription;

    public boolean sync = true;

    public boolean observe;

    public TickBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public boolean isRemoved() {
        return this.remove;
    }

    public void observe() {
        observe = true;
        sync = true;
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
    public TickBlockEntity getHolder() {
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
        var chunk = getChunk();
        if (chunk != null) {
            chunk.setUnsaved(true);
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
        super.setRemoved();
        chunk = null;
    }

    @Override
    public void clearRemoved() {
        this.remove = false;
        chunk = null;
        super.clearRemoved();
        if (level instanceof ServerLevel serverLevel) {
            tickDelay = offset;
            TaskHandler.enqueueTask(serverLevel, () -> tickDelay = 0, 1);
        }
    }

    @Override
    public void onValid() {
        if (level instanceof ServerLevel) {
            autoSyncSubscription = subscribeAsyncTick(autoSyncSubscription, this::autoSync, 5);
        }
    }

    @Override
    public void onInValid() {
        autoSyncSubscription = ITickSubscription.unsubscribe(autoSyncSubscription);
    }

    protected int periodID = offset;

    protected void autoSync() {
        asyncTick(periodID++);
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        if (getFieldDataManager().hasSyncToClientField()) {
            tag.putByteArray("fdms", getFieldDataManager().writeToNetworkBuffer(LogicalSide.SERVER, true));
        }
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.get("fdm") instanceof ByteArrayTag byteArrayTag) {
            getFieldDataManager().readFromData((MapData) Data.read(byteArrayTag.getAsByteArray()));
        }
        if (tag.get("fdms") instanceof ByteArrayTag byteArrayTag) {
            getFieldDataManager().readFromNetworkBuffer(LogicalSide.CLIENT, byteArrayTag.getAsByteArray());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putByteArray("fdm", getFieldDataManager().writeToData().writeToBytes());
    }

    @Override
    public void asyncTick(long periodID) {
        if (needSync() || periodID % 40 == 0) {
            var server = GTCEu.getMinecraftServer();
            if (server != null) {
                if (getFieldDataManager().updateSyncDirtyFlags(LogicalSide.SERVER, true)) {
                    var p = SCPacketSBlockEntitySync.of(this, false);
                    server.execute(() -> GTNetwork.NETWORK.sendToTrackingChunk(p, getChunk()));
                }
                for (IRef field : getNonLazyFields()) {
                    field.update();
                }
                if (getRootStorage().hasDirtySyncFields()) {
                    server.execute(() -> {
                        var packet = SPacketManagedPayload.of(this, false);
                        LDLNetworking.NETWORK.sendToTrackingChunk(packet, getChunk());
                    });
                }
            }
        }
    }

    @Override
    public void scheduleUpdate(LogicalSide side) {
        scheduleRenderUpdate();
    }

    @Override
    public TickBlockEntity getSelf() {
        return this;
    }
}
