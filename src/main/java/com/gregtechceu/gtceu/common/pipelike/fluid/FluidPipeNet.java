package com.gregtechceu.gtceu.common.pipelike.fluid;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.utils.collection.LoopIterator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class FluidPipeNet extends PipeNet<FluidPipeProperties> {

    private final Long2ObjectOpenHashMap<LoopIterator<FluidRoutePath>> netData = new Long2ObjectOpenHashMap<>();

    public FluidPipeNet(LevelPipeNet<FluidPipeProperties, ? extends PipeNet<FluidPipeProperties>> world) {
        super(world);
    }

    public LoopIterator<FluidRoutePath> getNetData(long pipePos, BlockPos pos, Direction facing) {
        var data = netData.get(pipePos);
        if (data == null) {
            var datas = FluidNetWalker.createNetData(this, pos, facing);
            if (datas == null) {
                // walker failed, don't cache so it tries again on next insertion
                return LoopIterator.EMPTY;
            }
            data = new LoopIterator<>(datas.toArray(new FluidRoutePath[0]));
            netData.put(pipePos, data);
        }
        return data;
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
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<FluidPipeProperties>> transferredNodes,
                                    PipeNet<FluidPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((FluidPipeNet) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(FluidPipeProperties nodeData, CompoundTag tagCompound) {
        tagCompound.putInt("throughput", nodeData.getThroughput());
    }

    @Override
    protected FluidPipeProperties readNodeData(CompoundTag tagCompound) {
        int throughput = tagCompound.getInt("throughput");
        return new FluidPipeProperties(throughput);
    }
}
