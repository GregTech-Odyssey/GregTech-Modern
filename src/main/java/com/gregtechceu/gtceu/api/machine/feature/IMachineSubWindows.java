package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.gui.factory.MachineSubWindowFactory;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

/**
 * 机器的独立窗口：功能多的机器把次要的大块功能（科技树、数据访问……）放进各自的窗口，由主界面上的按钮打开，
 * 而不是全挤在主界面的页面标签里。每个窗口有一个固定的键；打开用 {@link MachineSubWindowFactory#open}（服务端）。
 * <p>
 * 窗口与主界面一样以机器为 UI 持有者：机器失效、玩家走远时自动关闭。窗口里通常用 {@code MachineWindow}，
 * 并用 {@code MachineWindow#setBackToMachine} 在标题栏放"返回"按钮回到机器主界面。
 */
public interface IMachineSubWindows extends IUIMachine {

    /**
     * 按键创建窗口（两端都会调用，参数相同）；键不认识时返回 null（客户端可以伪造键，服务端据此拒绝打开）。
     */
    @Nullable
    ModularUI createSubWindow(String key, Player player);
}
