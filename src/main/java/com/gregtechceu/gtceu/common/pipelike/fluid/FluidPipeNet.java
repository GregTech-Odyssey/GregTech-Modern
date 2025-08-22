package com.gregtechceu.gtceu.common.pipelike.fluid;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FluidPipeNet extends PipeNet<FluidPipeProperties> {

    private final Long2ObjectOpenHashMap<List<FluidRoutePath>> NET_DATA = new Long2ObjectOpenHashMap<>();

    public FluidPipeNet(LevelPipeNet<FluidPipeProperties, ? extends PipeNet<FluidPipeProperties>> world) {
        super(world);
    }

    public List<FluidRoutePath> getNetData(long pipePos, BlockPos pos, Direction facing) {
        List<FluidRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = FluidNetWalker.createNetData(this, pos, facing);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(inv -> inv.getTargetPipe().isBlocked(inv.getTargetFacing()) ? 0 : 1));
            NET_DATA.put(pipePos, data);
        }
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
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<FluidPipeProperties>> transferredNodes,
                                    PipeNet<FluidPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((FluidPipeNet) parentNet).NET_DATA.clear();
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
