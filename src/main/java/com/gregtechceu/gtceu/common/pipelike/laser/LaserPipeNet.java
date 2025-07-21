package com.gregtechceu.gtceu.common.pipelike.laser;

import com.gregtechceu.gtceu.api.pipenet.PipeNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

public class LaserPipeNet extends PipeNet<LaserPipeProperties> {

    private final Long2ObjectOpenHashMap<LaserRoutePath> netData = new Long2ObjectOpenHashMap<>();

    public LaserPipeNet(LevelLaserPipeNet world) {
        super(world);
    }

    @Nullable
    public LaserRoutePath getNetData(long pipePos, BlockPos pos, Direction facing) {
        LaserRoutePath data = netData.get(pipePos);
        if (data == null) {
            data = LaserNetWalker.createNetData(this, pos, facing);
            if (data == null) {
                // walker failed, don't cache, so it tries again on next insertion
                return null;
            }
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
    protected void writeNodeData(LaserPipeProperties laserPipeProperties, CompoundTag compoundTag) {}

    @Override
    protected LaserPipeProperties readNodeData(CompoundTag tagCompound) {
        return LaserPipeProperties.INSTANCE;
    }
}
