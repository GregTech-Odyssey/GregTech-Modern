package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SCPacketStructureFormed implements IPacket {

    public static void addListener(Consumer<IMultiController> listener) {
        LISTENER.add(listener);
    }

    private static final List<Consumer<IMultiController>> LISTENER = new ArrayList<>();

    private long pos;
    private boolean formed;

    public SCPacketStructureFormed(long pos, boolean formed) {
        this.pos = pos;
        this.formed = formed;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(formed);
        buf.writeVarLong(pos);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        formed = buf.readBoolean();
        pos = buf.readVarLong();
    }

    @Override
    public void execute(IHandlerContext handler) {
        var level = GTUtil.getClientLevel();
        if (level == null) return;
        var cache = ILevel.getHighlightCache(level);
        if (formed) cache.remove(pos);
        else cache.add(pos);
        if (!LISTENER.isEmpty()) {
            var machine = MetaMachine.getMachine(level, BlockPos.of(pos));
            if (machine instanceof IMultiController controller) LISTENER.forEach(listener -> listener.accept(controller));
        }
    }

    public SCPacketStructureFormed() {}
}
