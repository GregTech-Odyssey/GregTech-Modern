package com.gregtechceu.gtceu.common.pipelike.item;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
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

public class ItemPipeNet extends PipeNet<ItemPipeProperties> {

    private final Long2ObjectOpenHashMap<List<ItemRoutePath>> NET_DATA = new Long2ObjectOpenHashMap<>();

    public ItemPipeNet(LevelPipeNet<ItemPipeProperties, ? extends PipeNet<ItemPipeProperties>> world) {
        super(world);
    }

    public List<ItemRoutePath> getNetData(long pipePos, BlockPos pos, Direction facing) {
        List<ItemRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = ItemNetWalker.createNetData(this, pos, facing);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(inv -> inv.getTargetPipe().isBlocked(inv.getTargetFacing()) ? 0 : inv.getProperties().getPriority()));
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
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<ItemPipeProperties>> transferredNodes,
                                    PipeNet<ItemPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((ItemPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(ItemPipeProperties nodeData, CompoundTag tagCompound) {
        tagCompound.putInt("Resistance", nodeData.getPriority());
        tagCompound.putFloat("Rate", nodeData.getTransferRate());
    }

    @Override
    protected ItemPipeProperties readNodeData(CompoundTag tagCompound) {
        return new ItemPipeProperties(tagCompound.getInt("Resistance"), tagCompound.getFloat("Rate"));
    }
}
