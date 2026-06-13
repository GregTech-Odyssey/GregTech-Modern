package com.gregtechceu.gtceu.common.blockentity;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.misc.ComputationProviderList;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.common.pipelike.optical.*;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

public final class OpticalPipeBlockEntity extends PipeBlockEntity<OpticalPipeType, OpticalPipeProperties> {

    private final EnumMap<Direction, OpticalNetHandler> handlers = new EnumMap<>(Direction.class);
    // the OpticalNetHandler can only be created on the server, so we have an empty placeholder for the client
    private static final IDataAccessHatch defaultDataHandler = new DefaultDataHandler();
    private WeakReference<OpticalPipeNet> currentPipeNet = new WeakReference<>(null);
    private OpticalNetHandler defaultHandler;
    @Getter
    @SaveToDisk
    @SyncToClient(notifyUpdate = true)
    private boolean isActive;

    public OpticalPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public boolean canHaveBlockedFaces() {
        return false;
    }

    private void initHandlers() {
        OpticalPipeNet net = getOpticalPipeNet();
        if (net == null) return;
        for (Direction facing : GTUtil.DIRECTIONS) {
            handlers.put(facing, new OpticalNetHandler(net, this, facing));
        }
        defaultHandler = new OpticalNetHandler(net, this, null);
    }

    @Override
    public @Nullable <T> Object getGTCapability(@NotNull Class<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.DATA_ACCESS) {
            if (level.isClientSide) return defaultDataHandler;
            if (side != null && !isConnected(side)) return GTCapability.EMPTY;
            if (handlers.isEmpty()) initHandlers();
            checkNetwork();
            return handlers.getOrDefault(side, defaultHandler);
        } else if (cap == GTCapability.COMPUTATION_PROVIDER) {
            if (level.isClientSide) return ComputationProviderList.EMPTY;
            if (side != null && !isConnected(side)) return GTCapability.EMPTY;
            if (handlers.isEmpty()) initHandlers();
            checkNetwork();
            return handlers.getOrDefault(side, defaultHandler);
        }
        return super.getGTCapability(cap, side);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            OpticalPipeNet current = getOpticalPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (OpticalNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
            }
        }
    }

    public OpticalPipeNet getOpticalPipeNet() {
        if (level == null || level.isClientSide) return null;
        OpticalPipeNet currentPipeNet = this.currentPipeNet.get();
        if (currentPipeNet != null && currentPipeNet.isValid() && currentPipeNet.containsNode(getPipeLongPos())) return currentPipeNet;
        LevelOpticalPipeNet worldNet = (LevelOpticalPipeNet) getPipeBlock().getWorldPipeNet((ServerLevel) getLevel());
        currentPipeNet = worldNet.getNetFromPos(getPipePos(), getPipeLongPos());
        if (currentPipeNet != null) {
            this.currentPipeNet = new WeakReference<>(currentPipeNet);
        }
        return currentPipeNet;
    }

    @Override
    public void setConnection(Direction side, boolean connected, boolean fromNeighbor) {
        if (!getLevel().isClientSide && connected && !fromNeighbor) {
            // never allow more than two connections total
            if (getNumConnections() >= 2) return;
            // also check the other pipe
            BlockEntity tile = getLevel().getBlockEntity(getPipePos().relative(side));
            if (tile instanceof PipeBlockEntity<?, ?> pipeTile && pipeTile.getPipeType().getClass() == this.getPipeType().getClass()) {
                if (pipeTile.getNumConnections() >= 2) return;
            }
        }
        super.setConnection(side, connected, fromNeighbor);
    }

    /**
     * @param active   if the pipe should become active
     * @param duration how long the pipe should be active for
     */
    public void setActive(boolean active, int duration) {
        boolean stateChanged = false;
        if (this.isActive && !active) {
            this.isActive = false;
            stateChanged = true;
        } else if (!this.isActive && active) {
            this.isActive = true;
            stateChanged = true;
            TaskHandler.enqueueTask((ServerLevel) getLevel(), () -> setActive(false, 0), duration);
        }
        if (stateChanged) {
            notifyBlockUpdate();
            setChanged();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.handlers.clear();
    }

    @Override
    public GTToolType getPipeTuneTool() {
        return GTToolType.WIRE_CUTTER;
    }

    private static class DefaultDataHandler implements IDataAccessHatch {

        @Override
        public boolean isRecipeAvailable(@NotNull GTRecipeDefinition recipe) {
            return false;
        }
    }
}
