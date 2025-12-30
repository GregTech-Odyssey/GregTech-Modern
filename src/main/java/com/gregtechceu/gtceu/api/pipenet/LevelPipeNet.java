package com.gregtechceu.gtceu.api.pipenet;

import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.PosUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class LevelPipeNet<NodeDataType, T extends PipeNet<NodeDataType>> extends SavedData {

    public final ServerLevel serverLevel;
    protected List<T> pipeNets = new ArrayList<>();
    protected final Long2ObjectOpenHashMap<List<T>> pipeNetsByChunk = new Long2ObjectOpenHashMap<>();

    public LevelPipeNet(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
    }

    public LevelPipeNet(ServerLevel serverLevel, CompoundTag tag) {
        this(serverLevel);
        this.pipeNets = new ArrayList<>();
        ListTag allEnergyNets = tag.getList("PipeNets", Tag.TAG_COMPOUND);
        for (int i = 0; i < allEnergyNets.size(); i++) {
            CompoundTag pNetTag = allEnergyNets.getCompound(i);
            T pipeNet = createNetInstance();
            pipeNet.deserializeNBT(pNetTag);
            addPipeNetSilently(pipeNet);
        }
    }

    public void addNode(BlockPos nodePos, NodeDataType nodeData, int mark, int openConnections, boolean isActive) {
        T myPipeNet = null;
        long pos = nodePos.asLong();
        Node<NodeDataType> node = new Node<>(nodeData, openConnections, mark, isActive);
        for (Direction facing : GTUtil.DIRECTIONS) {
            BlockPos offsetPos = nodePos.relative(facing);
            long offsetPosLong = offsetPos.asLong();
            T pipeNet = getNetFromPos(offsetPos, offsetPosLong);
            Node<NodeDataType> secondNode = pipeNet == null ? null : pipeNet.getNodeAt(offsetPosLong);
            if (pipeNet != null &&
                    pipeNet.canNodesConnect(secondNode, facing.getOpposite(), node)) {
                if (myPipeNet == null) {
                    myPipeNet = pipeNet;
                    myPipeNet.addNode(pos, node);
                } else if (myPipeNet != pipeNet) {
                    myPipeNet.uniteNetworks(pipeNet);
                }
            }

        }
        if (myPipeNet == null) {
            myPipeNet = createNetInstance();
            myPipeNet.addNode(pos, node);
            addPipeNet(myPipeNet);
            setDirty();
        }
    }

    protected void addPipeNetToChunk(long chunkPos, T pipeNet) {
        this.pipeNetsByChunk.computeIfAbsent(chunkPos, any -> new ArrayList<>()).add(pipeNet);
    }

    protected void removePipeNetFromChunk(long chunkPos, T pipeNet) {
        List<T> list = this.pipeNetsByChunk.get(chunkPos);
        if (list != null) {
            list.remove(pipeNet);
            if (list.isEmpty()) this.pipeNetsByChunk.remove(chunkPos);
        }
    }

    public void removeNode(BlockPos nodePos) {
        long posLong = nodePos.asLong();
        T pipeNet = getNetFromPos(nodePos, posLong);
        if (pipeNet != null) {
            pipeNet.removeNode(nodePos, posLong);
        }
    }

    public void updateBlockedConnections(BlockPos nodePos, long posLong, Direction side, boolean isBlocked) {
        T pipeNet = getNetFromPos(nodePos, posLong);
        if (pipeNet != null) {
            pipeNet.updateBlockedConnections(nodePos, posLong, side, isBlocked);
            pipeNet.onPipeConnectionsUpdate();
        }
    }

    public T getNetFromPos(BlockPos blockPos, long posLong) {
        List<T> pipeNetsInChunk = pipeNetsByChunk.getOrDefault(PosUtils.getChunkLong(blockPos), Collections.emptyList());
        for (T pipeNet : pipeNetsInChunk) {
            if (pipeNet.containsNode(posLong))
                return pipeNet;
        }
        return null;
    }

    protected void addPipeNet(T pipeNet) {
        addPipeNetSilently(pipeNet);
    }

    protected void addPipeNetSilently(T pipeNet) {
        this.pipeNets.add(pipeNet);
        pipeNet.getContainedChunks().forEach(chunkPos -> addPipeNetToChunk(chunkPos, pipeNet));
        pipeNet.isValid = true;
    }

    protected void removePipeNet(T pipeNet) {
        this.pipeNets.remove(pipeNet);
        pipeNet.getContainedChunks().forEach(chunkPos -> removePipeNetFromChunk(chunkPos, pipeNet));
        pipeNet.isValid = false;
        setDirty();
    }

    protected abstract T createNetInstance();

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag compound) {
        ListTag allPipeNets = new ListTag();
        for (T pipeNet : pipeNets) {
            CompoundTag pNetTag = pipeNet.serializeNBT();
            allPipeNets.add(pNetTag);
        }
        compound.put("PipeNets", allPipeNets);
        return compound;
    }
}
