package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineSubWindows;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

/**
 * 打开机器的独立窗口（见 {@link IMachineSubWindows}）：同步数据是机器坐标 + 窗口键，两端各自按键建窗口。
 */
public class MachineSubWindowFactory extends UIFactory<MachineSubWindowFactory.Holder> {

    public static final MachineSubWindowFactory INSTANCE = new MachineSubWindowFactory();
    /** 窗口键的最大长度。 */
    public static final int MAX_KEY_LENGTH = 64;

    /** 机器与要打开的窗口键。 */
    public record Holder(MetaMachine machine, String key) {}

    public MachineSubWindowFactory() {
        super(GTCEu.id("machine_sub_window"));
    }

    /** 服务端：给玩家打开机器的某个独立窗口（替换当前打开的界面）。机器不支持该键时不打开，返回 false。 */
    public static boolean open(ServerPlayer player, MetaMachine machine, String key) {
        if (!(machine instanceof IMachineSubWindows) || key.length() > MAX_KEY_LENGTH) return false;
        return INSTANCE.openUI(new Holder(machine, key), player);
    }

    /** 服务端：回到机器主界面。 */
    public static boolean openMachine(ServerPlayer player, MetaMachine machine) {
        return MachineUIFactory.INSTANCE.openUI(machine, player);
    }

    @Override
    @Nullable
    protected ModularUI createUITemplate(Holder holder, Player entityPlayer) {
        return holder.machine() instanceof IMachineSubWindows windows ? windows.createSubWindow(holder.key(), entityPlayer) : null;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    @Nullable
    protected Holder readHolderFromSyncData(FriendlyByteBuf syncData) {
        Level world = Minecraft.getInstance().level;
        var pos = syncData.readBlockPos();
        var key = syncData.readUtf(MAX_KEY_LENGTH);
        if (world == null) return null;
        if (world.getBlockEntity(pos) instanceof MetaMachineBlockEntity holder) {
            return new Holder(holder.getMetaMachine(), key);
        }
        return null;
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, Holder holder) {
        syncData.writeBlockPos(holder.machine().getPos());
        syncData.writeUtf(holder.key(), MAX_KEY_LENGTH);
    }
}
