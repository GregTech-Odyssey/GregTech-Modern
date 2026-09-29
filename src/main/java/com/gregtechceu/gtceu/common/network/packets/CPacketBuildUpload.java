package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.machine.multiblockpro.BuildUpload;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class CPacketBuildUpload implements IPacket {

    private static final byte BEGIN = 0;
    private static final byte CELLS = 1;

    private byte kind = -1;
    private int id;
    private ResourceLocation definition;
    private int[] values = new int[0];
    private int cells;
    private Item[] palette = new Item[0];
    private int offset;
    private int[] runs = new int[0];

    public CPacketBuildUpload() {}

    public static CPacketBuildUpload begin(int id, ResourceLocation definition, int[] values, int cells, Item[] palette) {
        var packet = new CPacketBuildUpload();
        packet.kind = BEGIN;
        packet.id = id;
        packet.definition = definition;
        packet.values = values;
        packet.cells = cells;
        packet.palette = palette;
        return packet;
    }

    public static CPacketBuildUpload cells(int id, int offset, int[] runs) {
        var packet = new CPacketBuildUpload();
        packet.kind = CELLS;
        packet.id = id;
        packet.offset = offset;
        packet.runs = runs;
        return packet;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeVarInt(id);
        if (kind == BEGIN) {
            buf.writeResourceLocation(definition);
            buf.writeVarIntArray(values);
            buf.writeVarInt(cells);
            buf.writeVarInt(palette.length - 1);
            for (int i = 1; i < palette.length; i++) buf.writeVarInt(BuiltInRegistries.ITEM.getId(palette[i]));
        } else {
            buf.writeVarInt(offset);
            buf.writeVarInt(runs.length / 2);
            for (int run : runs) buf.writeVarInt(run);
        }
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        kind = buf.readByte();
        id = buf.readVarInt();
        if (kind == BEGIN) {
            definition = buf.readResourceLocation();
            values = buf.readVarIntArray(BuildUpload.MAX_VALUES);
            cells = buf.readVarInt();
            int size = buf.readVarInt();
            if (cells < 0 || cells > BuildUpload.MAX_CELLS || size < 0 || size > BuildUpload.MAX_PALETTE || size > buf.readableBytes()) {
                kind = -1;
                return;
            }
            palette = new Item[size + 1];
            for (int i = 1; i < palette.length; i++) {
                var item = BuiltInRegistries.ITEM.byId(buf.readVarInt());
                palette[i] = item == Items.AIR ? null : item;
            }
        } else if (kind == CELLS) {
            offset = buf.readVarInt();
            int count = buf.readVarInt();
            if (offset < 0 || count < 0 || count > buf.readableBytes() / 2) {
                kind = -1;
                return;
            }
            runs = new int[count * 2];
            for (int i = 0; i < runs.length; i++) runs[i] = buf.readVarInt();
        } else {
            kind = -1;
        }
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (!(handler.getPlayer() instanceof ServerPlayer player)) return;
        if (kind == BEGIN) BuildUpload.receiveBegin(player, id, definition, values, cells, palette);
        else if (kind == CELLS) BuildUpload.receiveCells(player, id, offset, runs);
    }
}
