package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyCustomMiddleClickAction;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyCustomMouseWheelAction;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.uiwidgets.circuit.CircuitSelector;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    final CustomItemStackHandler circuitSlot;

    public CircuitFancyConfigurator(CustomItemStackHandler circuitSlot) {
        this.circuitSlot = circuitSlot;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtceu.gui.circuit.title");
    }

    @Override
    public IGuiTexture getIcon() {
        if (IntCircuitBehaviour.isIntegratedCircuit(circuitSlot.getStackInSlot(0))) {
            return new ItemStackTexture(circuitSlot.getStackInSlot(0));
        }
        return new GuiTextureGroup(new ItemStackTexture(IntCircuitBehaviour.stack(0)),
                new ItemStackTexture(Items.BARRIER));
    }

    @Override
    public boolean mouseWheelMove(BiConsumer<Integer, Consumer<FriendlyByteBuf>> writeClientAction, double mouseX,
                                  double mouseY, double wheelDelta) {
        if (wheelDelta == 0) return false;
        int nextValue = getNextValue(wheelDelta > 0);
        if (nextValue == NO_CONFIG) {
            circuitSlot.setStackInSlot(0, ItemStack.EMPTY);
            writeClientAction.accept(SET_TO_EMPTY, buf -> {});
        } else {
            circuitSlot.setStackInSlot(0, IntCircuitBehaviour.stack(nextValue));
            writeClientAction.accept(SET_TO_N, buf -> buf.writeVarInt(nextValue));
        }
        return true;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        switch (id) {
            case SET_TO_ZERO -> circuitSlot.setStackInSlot(0, IntCircuitBehaviour.stack(0));
            case SET_TO_EMPTY -> circuitSlot.setStackInSlot(0, ItemStack.EMPTY);
            case SET_TO_N -> circuitSlot.setStackInSlot(0, IntCircuitBehaviour.stack(buffer.readVarInt()));
        }
    }

    @Override
    public void onMiddleClick(BiConsumer<Integer, Consumer<FriendlyByteBuf>> writeClientAction) {
        if (!circuitSlot.getStackInSlot(0).isEmpty())
            circuitSlot.setStackInSlot(0, IntCircuitBehaviour.stack(0));
        else
            circuitSlot.setStackInSlot(0, ItemStack.EMPTY);
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
        int currentValue = IntCircuitBehaviour.getCircuitConfiguration(circuitSlot.getStackInSlot(0));
        if (increment) {
            // if at max, loop around to no circuit
            if (currentValue == IntCircuitBehaviour.CIRCUIT_MAX) {
                return 0;
            }
            // if at no circuit, skip 0 and return 1
            if (this.circuitSlot.getStackInSlot(0).isEmpty()) {
                return 1;
            }
            // normal case: increment by 1
            return currentValue + 1;
        } else {
            // if at no circuit, loop around to max
            if (this.circuitSlot.getStackInSlot(0).isEmpty()) {
                return IntCircuitBehaviour.CIRCUIT_MAX;
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
