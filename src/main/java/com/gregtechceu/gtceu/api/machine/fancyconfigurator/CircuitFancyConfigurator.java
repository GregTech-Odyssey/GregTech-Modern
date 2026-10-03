package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyCustomMiddleClickAction;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyCustomMouseWheelAction;
import com.gregtechceu.gtceu.api.recipe.content.Circuits;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.uiwidgets.circuit.CircuitSelector;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import appeng.api.stacks.AEItemKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CircuitFancyConfigurator implements IFancyConfigurator, IFancyCustomMouseWheelAction,
                                      IFancyCustomMiddleClickAction {

    private static final int SET_TO_ZERO = 2;
    private static final int SET_TO_EMPTY = 3;
    private static final int SET_TO_N = 4;

    private static final int NO_CONFIG = -1;

    final KeyInventory<AEItemKey> circuitSlot;

    public CircuitFancyConfigurator(KeyInventory<AEItemKey> circuitSlot) {
        this.circuitSlot = circuitSlot;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtceu.gui.circuit.title");
    }

    @Override
    public IGuiTexture getIcon() {
        if (Circuits.get(circuitSlot, 0) >= 0) {
            return new ItemStackTexture(Keys.displayStack(circuitSlot.keyAt(0)));
        }
        return WidgetIcons.CIRCUIT_NONE;
    }

    @Override
    public boolean mouseWheelMove(BiConsumer<Integer, Consumer<FriendlyByteBuf>> writeClientAction, double mouseX,
                                  double mouseY, double wheelDelta) {
        if (wheelDelta == 0) return false;
        int nextValue = getNextValue(wheelDelta > 0);
        if (nextValue == NO_CONFIG) {
            Circuits.set(circuitSlot, 0, NO_CONFIG);
            writeClientAction.accept(SET_TO_EMPTY, buf -> {});
        } else {
            Circuits.set(circuitSlot, 0, nextValue);
            writeClientAction.accept(SET_TO_N, buf -> buf.writeVarInt(nextValue));
        }
        return true;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        switch (id) {
            case SET_TO_ZERO -> Circuits.set(circuitSlot, 0, 0);
            case SET_TO_EMPTY -> Circuits.set(circuitSlot, 0, NO_CONFIG);
            case SET_TO_N -> {
                // 编号来自客户端，越界（伪造包）直接忽略
                int n = buffer.readVarInt();
                if (n >= 0 && n <= Circuits.MAX) Circuits.set(circuitSlot, 0, n);
            }
        }
    }

    @Override
    public void onMiddleClick(BiConsumer<Integer, Consumer<FriendlyByteBuf>> writeClientAction) {
        if (circuitSlot.amountAt(0) != 0)
            Circuits.set(circuitSlot, 0, 0);
        else
            Circuits.set(circuitSlot, 0, NO_CONFIG);
        writeClientAction.accept(SET_TO_EMPTY, buf -> {});
    }

    /** 电路设置展开后的内容：新式电路选择器（{@link CircuitSelector}）。 */
    @Override
    public Widget createConfigurator() {
        return CircuitSelector.create(circuitSlot);
    }

    @Override
    public List<Component> getTooltips() {
        var list = new ArrayList<>(IFancyConfigurator.super.getTooltips());
        list.addAll(Arrays.stream(
                LangHandler.getMultiLang("gtceu.gui.configurator_slot.tooltip").toArray(new MutableComponent[0]))
                .toList());
        return list;
    }

    private int getNextValue(boolean increment) {
        int currentValue = Circuits.get(circuitSlot, 0);
        if (increment) {
            // if at max, loop around to no circuit
            if (currentValue == Circuits.MAX) {
                return 0;
            }
            // if at no circuit, skip 0 and return 1
            if (this.circuitSlot.amountAt(0) == 0) {
                return 1;
            }
            // normal case: increment by 1
            return currentValue + 1;
        } else {
            // if at no circuit, loop around to max
            if (this.circuitSlot.amountAt(0) == 0) {
                return Circuits.MAX;
            }
            // if at 1, skip 0 and return no circuit
            if (currentValue == 1) {
                return -1;
            }
            // normal case: decrement by 1
            return currentValue - 1;
        }
    }
}
