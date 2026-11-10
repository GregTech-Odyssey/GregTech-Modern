package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.machine.multiblockpro.BuildUpload;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureBuild;
import com.gregtechceu.gtceu.api.machine.multiblockpro.TerminalBuildPlans;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;

public class CPacketSaveBuildPlan implements IPacket {

    private ResourceLocation definition = new ResourceLocation("minecraft", "air");
    private int[] values = new int[0];
    private int upload;
    private int flags;
    private Set<StructureBuild.Swap> kept = Collections.emptySet();

    public CPacketSaveBuildPlan() {}

    public CPacketSaveBuildPlan(ResourceLocation definition, int[] values, int upload, int flags, Set<StructureBuild.Swap> kept) {
        this(definition, values, upload, flags);
        this.kept = kept;
    }

    public CPacketSaveBuildPlan(ResourceLocation definition, int[] values, int upload, int flags) {
        this.definition = definition;
        this.values = values;
        this.upload = upload;
        this.flags = flags;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(definition);
        buf.writeVarIntArray(values);
        buf.writeVarInt(upload);
        buf.writeVarInt(flags);
        StructureBuild.Swap.write(buf, kept);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        definition = buf.readResourceLocation();
        values = buf.readVarIntArray(BuildUpload.MAX_VALUES);
        upload = buf.readVarInt();
        flags = buf.readVarInt() & StructureBuild.ALL_FLAGS;
        kept = StructureBuild.Swap.read(buf);
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (!(handler.getPlayer() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) return;
        TerminalBuildPlans.save(player, definition, values, upload, flags, kept);
    }
}
