package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.BuildUpload;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureBuild;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CPacketStructureBuild implements IPacket {

    private static final double MAX_DISTANCE_SQR = 64 * 64;
    private static final int COOLDOWN = 40;

    private BlockPos pos = BlockPos.ZERO;
    private int[] values = new int[0];
    private int upload;

    public CPacketStructureBuild() {}

    public CPacketStructureBuild(BlockPos pos, int[] values, int upload) {
        this.pos = pos;
        this.values = values;
        this.upload = upload;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarIntArray(values);
        buf.writeVarInt(upload);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        pos = buf.readBlockPos();
        values = buf.readVarIntArray(BuildUpload.MAX_VALUES);
        upload = buf.readVarInt();
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (!(handler.getPlayer() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) return;
        var terminal = GTItems.TERMINAL.get();
        if (!player.getMainHandItem().is(terminal) && !player.getOffhandItem().is(terminal)) return;
        if (player.getCooldowns().isOnCooldown(terminal)) return;
        if (player.distanceToSqr(pos.getCenter()) > MAX_DISTANCE_SQR || !player.level().isLoaded(pos)) return;
        if (!(MetaMachine.getMachine(player.level(), pos) instanceof IMultiController controller)) return;
        player.getCooldowns().addCooldown(terminal, COOLDOWN);
        var definition = controller.self().getDefinition();
        var structure = definition.displayStructure();
        var layout = structure == null ? null : structure.layout(values);
        var choices = layout == null ? null : BuildUpload.take(player, upload, definition, values, layout.cells().size());
        if (choices == null) {
            player.sendSystemMessage(Component.translatable(StructureBuild.INVALID));
            return;
        }
        StructureBuild.build(player, controller, layout, choices);
    }
}
