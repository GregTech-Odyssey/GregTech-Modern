package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.uiwidgets.mode.ModeSelector;

import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import com.gto.datasynclib.datastream.codec.ByteStreamDecoder;
import com.gto.datasynclib.datastream.codec.ByteStreamEncoder;

import java.util.ArrayList;
import java.util.List;

public class MachineModeFancyConfigurator implements IFancyUIProvider {

    protected IRecipeLogicMachine machine;

    public MachineModeFancyConfigurator(IRecipeLogicMachine machine) {
        this.machine = machine;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtceu.gui.machinemode.title");
    }

    @Override
    public IGuiTexture getTabIcon() {
        return new ItemStackTexture(GTItems.ROBOT_ARM_LV.get());
    }

    /**
     * 机器模式页：新式模式选择（{@link ModeSelector}）。另挂一个不占位置的 {@link MachineModeConfigurator}，
     * 沿用原来的做法在打开界面时把可用配方类型下发给客户端机器。
     */
    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        var types = machine.getAvailableRecipeTypes();
        return ModeSelector.create(types.length, i -> Component.translatable(types[i].registryName.toLanguageKey()),
                machine::getActiveRecipeType, machine::setActiveRecipeType)
                .addChild(new MachineModeConfigurator(0, 0, 0, 0));
    }

    @Override
    public List<Component> getTabTooltips() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gtceu.gui.machinemode.tab_tooltip"));
        return tooltip;
    }

    public class MachineModeConfigurator extends WidgetGroup {

        public MachineModeConfigurator(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        public void writeInitialData(FriendlyByteBuf buffer) {
            buffer.writeVarInt(machine.getActiveRecipeType());
            ByteStreamEncoder.array(GTRegistries.RECIPE_TYPES.streamCodec()).encode(buffer, machine.getAvailableRecipeTypes());
        }

        @Override
        public void readInitialData(FriendlyByteBuf buffer) {
            machine.setActiveRecipeType(buffer.readVarInt());
            machine.setAvailableRecipeTypesCache(ByteStreamDecoder.list(GTRegistries.RECIPE_TYPES.streamCodec()).decode(buffer).toArray(new GTRecipeType[0]));
        }

        @Override
        public void detectAndSendChanges() {
            this.writeUpdateInfo(0, buf -> buf.writeVarInt(machine.getActiveRecipeType()));
        }

        @Override
        public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
            if (id == 0) {
                machine.setActiveRecipeType(buffer.readVarInt());
            }
        }
    }
}
