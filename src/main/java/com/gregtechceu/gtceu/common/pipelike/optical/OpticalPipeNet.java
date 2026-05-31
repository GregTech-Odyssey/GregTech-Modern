package com.gregtechceu.gtceu.common.pipelike.optical;

import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

public class OpticalPipeNet extends PipeNet<OpticalPipeProperties> {

    private final Long2ObjectOpenHashMap<OpticalRoutePath> netData = new Long2ObjectOpenHashMap<>();

    public OpticalPipeNet(LevelPipeNet<OpticalPipeProperties, ? extends PipeNet<OpticalPipeProperties>> world) {
        super(world);
    }

    @Nullable
    public OpticalRoutePath getNetData(long pipePos, BlockPos pos, Direction facing) {
        var path = netData.get(pipePos);
        if (path != null) return path;
        path = OpticalNetWalker.createNetData(this, pos, facing);
        if (path == null) return null;
        netData.put(pipePos, path);
        return path;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        netData.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        netData.clear();
    }

    @Override
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<OpticalPipeProperties>> transferredNodes,
                                    PipeNet<OpticalPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((OpticalPipeNet) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(OpticalPipeProperties nodeData, CompoundTag tagCompound) {}

    @Override
    protected OpticalPipeProperties readNodeData(CompoundTag tagCompound) {
        return OpticalPipeProperties.INSTANCE;
    }
}
