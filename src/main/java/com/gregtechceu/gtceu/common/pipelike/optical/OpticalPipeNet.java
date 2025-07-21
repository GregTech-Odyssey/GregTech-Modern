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

    private final Long2ObjectOpenHashMap<OpticalRoutePath> NET_DATA = new Long2ObjectOpenHashMap<>();

    public OpticalPipeNet(LevelPipeNet<OpticalPipeProperties, ? extends PipeNet<OpticalPipeProperties>> world) {
        super(world);
    }

    @Nullable
    public OpticalRoutePath getNetData(long pipePos, BlockPos pos, Direction facing) {
        if (NET_DATA.containsKey(pipePos)) {
            return NET_DATA.get(pipePos);
        }
        OpticalRoutePath data = OpticalNetWalker.createNetData(this, pos, facing);
        if (data == null) {
            // walker failed, don't cache, so it tries again on next insertion
            return null;
        }

        NET_DATA.put(pipePos, data);
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        NET_DATA.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        NET_DATA.clear();
    }

    @Override
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<OpticalPipeProperties>> transferredNodes,
                                    PipeNet<OpticalPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((OpticalPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(OpticalPipeProperties nodeData, CompoundTag tagCompound) {}

    @Override
    protected OpticalPipeProperties readNodeData(CompoundTag tagCompound) {
        return OpticalPipeProperties.INSTANCE;
    }
}
