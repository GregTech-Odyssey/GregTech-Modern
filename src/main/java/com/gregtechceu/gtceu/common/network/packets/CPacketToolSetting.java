package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.item.tool.ToolSetting;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class CPacketToolSetting implements IPacket {

    private boolean switchSetting;

    public CPacketToolSetting() {}

    public CPacketToolSetting(boolean switchSetting) {
        this.switchSetting = switchSetting;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(switchSetting);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        this.switchSetting = buf.readBoolean();
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (handler.getPlayer() instanceof ServerPlayer player) {
            ToolSetting.onKey(player, switchSetting);
        }
    }
}
