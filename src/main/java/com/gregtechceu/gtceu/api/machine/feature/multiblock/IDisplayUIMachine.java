package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.display.MachineDisplay;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public interface IDisplayUIMachine extends IUIMachine, IMultiController {

    default void addDisplayText(List<Component> textList) {
        for (var part : this.getParts()) {
            part.addMultiText(textList);
        }
    }

    default void readClientTextData(FriendlyByteBuf buf, List<Component> textList) {}

    default void writeClientTextData(FriendlyByteBuf buf) {}

    default void handleDisplayClick(String componentData, ClickData clickData) {}

    default IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY;
    }

    /**
     * 没有实现 {@code IFancyUIMachine} 的显示类机器（如蒸汽多方块）也用新式机器窗口，主页是状态显示窗
     * （{@link MachineDisplay#provider}）；原来是独立的深色显示屏 + 玩家背包。
     */
    @Override
    default ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 216, this, entityPlayer).widget(new MachineWindow(MachineDisplay.provider(this)));
    }
}
