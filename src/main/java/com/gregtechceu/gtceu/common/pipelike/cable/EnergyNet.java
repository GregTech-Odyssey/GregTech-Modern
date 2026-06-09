package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.utils.collection.LoopIterator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class EnergyNet extends PipeNet<WireProperties> {

    private final Long2ObjectOpenHashMap<LoopIterator<EnergyRoutePath>> netData = new Long2ObjectOpenHashMap<>();

    EnergyNet(LevelPipeNet<WireProperties, ? extends EnergyNet> world) {
        super(world);
    }

    public LoopIterator<EnergyRoutePath> getNetData(long pipePos, BlockPos pos) {
        var data = netData.get(pipePos);
        if (data == null) {
            var datas = EnergyNetWalker.createNetData(this, pos);
            if (datas == null) {
                // walker failed, don't cache so it tries again on next insertion
                return LoopIterator.EMPTY;
            }
            data = new LoopIterator<>(datas.toArray(new EnergyRoutePath[0]));
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
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<WireProperties>> transferredNodes,
                                    PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((EnergyNet) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(WireProperties nodeData, CompoundTag tagCompound) {
        tagCompound.putLong("voltage", nodeData.getVoltage());
        tagCompound.putInt("amperage", nodeData.getAmperage());
        tagCompound.putInt("loss", nodeData.getLossPerBlock());
    }

    @Override
    protected WireProperties readNodeData(CompoundTag tagCompound) {
        long voltage = tagCompound.getLong("voltage");
        int amperage = tagCompound.getInt("amperage");
        int lossPerBlock = tagCompound.getInt("loss");
        return new WireProperties(voltage, amperage, lossPerBlock);
    }
}
