package com.gregtechceu.gtceu.common.pipelike.item;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.utils.collection.LoopIterator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Comparator;

public final class ItemPipeNet extends PipeNet<ItemPipeProperties> {

    private final Long2ObjectOpenHashMap<LoopIterator<ItemRoutePath>> netData = new Long2ObjectOpenHashMap<>();

    public ItemPipeNet(LevelPipeNet<ItemPipeProperties, ? extends PipeNet<ItemPipeProperties>> world) {
        super(world);
    }

    public LoopIterator<ItemRoutePath> getNetData(long pipePos, BlockPos pos, Direction facing) {
        var data = netData.get(pipePos);
        if (data == null) {
            var datas = ItemNetWalker.createNetData(this, pos, facing);
            if (datas == null) {
                // walker failed, don't cache so it tries again on next insertion
                return LoopIterator.EMPTY;
            }
            datas.sort(Comparator.comparingInt(inv -> inv.getTargetPipe().isBlocked(inv.getTargetFacing()) ? 0 : inv.getProperties().getPriority()));
            data = new LoopIterator<>(datas.toArray(new ItemRoutePath[0]));
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
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<ItemPipeProperties>> transferredNodes,
                                    PipeNet<ItemPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((ItemPipeNet) parentNet).netData.clear();
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
