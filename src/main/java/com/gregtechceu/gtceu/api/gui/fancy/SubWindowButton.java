package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.factory.MachineSubWindowFactory;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineSubWindows;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 机器小组件（窗口左侧配置按钮）里的"打开独立窗口"按钮：点击后服务端给点击的玩家打开机器的某个独立窗口
 * （{@link IMachineSubWindows}），当前界面被替换；独立窗口标题栏的"返回"回到机器主界面。
 */
public class SubWindowButton implements IFancyConfiguratorButton {

    private final MetaMachine machine;
    private final String key;
    private final IGuiTexture icon;
    private final List<Component> tooltips;

    /**
     * @param machine  实现了 {@link IMachineSubWindows} 的机器
     * @param key      窗口键
     * @param icon     按钮图标
     * @param tooltips 悬停说明（第一行是窗口名称）
     */
    public SubWindowButton(IMachineSubWindows machine, String key, IGuiTexture icon, Component... tooltips) {
        this.machine = machine.self();
        this.key = key;
        this.icon = icon;
        this.tooltips = List.of(tooltips);
    }

    @Override
    public IGuiTexture getIcon() {
        return icon;
    }

    @Override
    public List<Component> getTooltips() {
        return tooltips;
    }

    @Override
    public void onClick(ClickData clickData) {}

    @Override
    public void onClick(ClickData clickData, @Nullable Player player) {
        if (!clickData.isRemote && player instanceof ServerPlayer serverPlayer) {
            MachineSubWindowFactory.open(serverPlayer, machine, key);
        }
    }
}
