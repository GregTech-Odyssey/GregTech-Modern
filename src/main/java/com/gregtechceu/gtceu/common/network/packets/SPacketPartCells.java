package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.machine.multiblockpro.PartCells;
import com.gregtechceu.gtceu.uiwidgets.structure.PartCellsOverlay;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.network.FriendlyByteBuf;

public class SPacketPartCells implements IPacket {

    private PartCells.Result result;

    public SPacketPartCells() {}

    public SPacketPartCells(PartCells.Result result) {
        this.result = result;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        result.write(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        result = PartCells.Result.read(buf);
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (result != null) PartCellsOverlay.show(result);
    }
}
