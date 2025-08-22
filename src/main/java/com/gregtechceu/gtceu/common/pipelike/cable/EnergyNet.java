package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EnergyNet extends PipeNet<WireProperties> {

    private final Long2ObjectOpenHashMap<List<EnergyRoutePath>> NET_DATA = new Long2ObjectOpenHashMap<>();

    protected EnergyNet(LevelPipeNet<WireProperties, ? extends EnergyNet> world) {
        super(world);
    }

    public List<EnergyRoutePath> getNetData(long pipePos, BlockPos pos) {
        List<EnergyRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = EnergyNetWalker.createNetData(this, pos);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(EnergyRoutePath::getDistance));
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
    protected void transferNodeData(Long2ObjectOpenHashMap<Node<WireProperties>> transferredNodes,
                                    PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((EnergyNet) parentNet).NET_DATA.clear();
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
