package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ILaserContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.common.pipelike.laser.*;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

public class LaserPipeBlockEntity extends PipeBlockEntity<LaserPipeType, LaserPipeProperties> {

    @Getter
    protected final EnumMap<Direction, LaserNetHandler> handlers = new EnumMap<>(Direction.class);
    private WeakReference<LaserPipeNet> currentPipeNet = new WeakReference<>(null);
    protected LaserNetHandler defaultHandler;
    private int ticksActive = 0;
    private int activeDuration = 0;
    @Getter
    @Persisted
    @SyncToClient
    private boolean active = false;

    protected LaserPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static LaserPipeBlockEntity create(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new LaserPipeBlockEntity(type, pos, blockState);
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.CAPABILITY_LASER) {
            if (getLevel().isClientSide()) return GTCapability.CAPABILITY_LASER.orEmpty(cap, LazyOptional.of(() -> ILaserContainer.DEFAULT));
            if (side != null && !isConnected(side)) return LazyOptional.empty();
            if (handlers.isEmpty()) {
                initHandlers();
            }
            checkNetwork();
            var handler = handlers.getOrDefault(side, defaultHandler);
            return GTCapability.CAPABILITY_LASER.orEmpty(cap, LazyOptional.of(() -> handler == null ? ILaserContainer.DEFAULT : handler));
        } else if (cap == GTCapability.CAPABILITY_COVERABLE) {
            return GTCapability.CAPABILITY_COVERABLE.orEmpty(cap, LazyOptional.of(this::getCoverContainer));
        }
        return super.getCapability(cap, side);
    }

    @Override
    public boolean canHaveBlockedFaces() {
        return false;
    }

    public void initHandlers() {
        LaserPipeNet net = getLaserPipeNet();
        if (net == null) return;
        for (Direction facing : GTUtil.DIRECTIONS) {
            handlers.put(facing, new LaserNetHandler(net, this, facing));
        }
        defaultHandler = new LaserNetHandler(net, this, null);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            LaserPipeNet current = getLaserPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (LaserNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
            }
        }
    }

    public LaserPipeNet getLaserPipeNet() {
        if (level == null || level.isClientSide) {
            return null;
        }
        LaserPipeNet currentPipeNet = this.currentPipeNet.get();
        if (currentPipeNet != null && currentPipeNet.isValid() && currentPipeNet.containsNode(getPipePosLong())) {
            return currentPipeNet;
        }
        LevelLaserPipeNet worldNet = (LevelLaserPipeNet) getPipeBlock().getWorldPipeNet((ServerLevel) getLevel());
        currentPipeNet = worldNet.getNetFromPos(getPipePos(), getPipePosLong());
        if (currentPipeNet != null) {
            this.currentPipeNet = new WeakReference<>(currentPipeNet);
        }
        return currentPipeNet;
    }

    /**
     * @param active   if the pipe should become active
     * @param duration how long the pipe should be active for
     */
    public void setActive(boolean active, int duration) {
        if (this.active != active) {
            this.active = active;
            notifyBlockUpdate();
            setChanged();
            if (active && duration != this.activeDuration) {
                TaskHandler.enqueueTask(getLevel(), this::queueDisconnect, 0);
            }
        }
        this.activeDuration = duration;
        if (duration > 0 && active) {
            this.ticksActive = 0;
        }
    }

    public boolean queueDisconnect() {
        if (++this.ticksActive % activeDuration == 0) {
            this.ticksActive = 0;
            setActive(false, -1);
            return false;
        }
        return true;
    }

    @Override
    public void setConnection(Direction side, boolean connected, boolean fromNeighbor) {
        if (!getLevel().isClientSide && connected) {
            int connections = getConnections();
            // block connection if any side other than the requested side and its opposite side are already connected.
            connections &= ~(1 << side.ordinal());
            connections &= ~(1 << side.getOpposite().ordinal());
            if (connections != 0) return;
            // check the same for the targeted pipe
            BlockEntity tile = getNeighbor(side);
            if (tile instanceof PipeBlockEntity<?, ?> pipeTile && pipeTile.getPipeType().getClass() == this.getPipeType().getClass()) {
                connections = pipeTile.getConnections();
                connections &= ~(1 << side.ordinal());
                connections &= ~(1 << side.getOpposite().ordinal());
                if (connections != 0) return;
            }
        }
        super.setConnection(side, connected, fromNeighbor);
    }

    @Override
    public GTToolType getPipeTuneTool() {
        return GTToolType.WIRE_CUTTER;
    }
}
