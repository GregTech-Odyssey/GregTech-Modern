package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.MachineModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverTab;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.structure.MachinePreviewScene;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.ArrayList;
import java.util.List;

public interface IFancyUIMachine extends IUIMachine, IFancyUIProvider {

    @Override
    default ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 166, this, entityPlayer).widget(new MachineWindow(this));
    }

    @Override
    default boolean showsWindowLogo() {
        return !(self() instanceof IMultiController);
    }

    /**
     * We should not override this method in general, and use {@link IFancyUIMachine#createUIWidget()} instead,
     */
    @Override
    default Widget createMainPage(FancyMachineUIWidget widget) {
        var editableUI = self().getDefinition().getEditableUI();
        if (editableUI != null) {
            var template = editableUI.createCustomUI();
            if (template == null) {
                template = editableUI.createDefault();
            }
            editableUI.setupUI(template, self());
            return template;
        }
        return createUIWidget();
    }

    /**
     * Create the core widget of this machine.
     */
    default Widget createUIWidget() {
        var group = new UIElement().layout(l -> l.size(100, 100));
        if (isRemote()) {
            var plate = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left((100 - 48) / 2).top(60))
                    .addChild(new ImageWidget(0, 0, 48, 16, GuiTextures.SCENE));
            plate.setClientSideWidget();
            group.addChild(plate);
            group.addChild(MachinePreviewScene.create(self().getLevel(), self().getPos(), 100));
        }
        return group;
    }

    @Override
    default IGuiTexture getTabIcon() {
        return new ItemStackTexture(self().getDefinition().asItem());
    }

    @Override
    default void attachSideTabs(TabsWidget sideTabs) {
        sideTabs.setMainTab(this);

        if (this instanceof IRecipeLogicMachine rLMachine && rLMachine.getAvailableRecipeTypes().length > 1) {
            sideTabs.attachSubTab(new MachineModeFancyConfigurator(rLMachine));
        }
        CoverTab.attach(sideTabs, self());
    }

    @Override
    default void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        if (this instanceof IControllable controllable) {
            configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                    WidgetIcons.POWER_OFF,
                    WidgetIcons.POWER_ON,
                    controllable::isWorkingEnabled, (clickData, pressed) -> controllable.setWorkingEnabled(pressed))
                    .setTooltipsSupplier(pressed -> List.of(
                            Component.translatable(
                                    pressed ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled"))));
        }
        if (this instanceof MetaMachine machine) {
            for (var direction : Direction.values()) {
                if (machine.getCoverContainer().hasCover(direction)) {
                    var configurator = machine.getCoverContainer().getCoverAtSide(direction).getConfigurator();
                    if (configurator != null)
                        configuratorPanel.attachConfigurators(configurator);
                }
            }
        }
    }

    @Override
    default void attachTooltips(TooltipsPanel tooltipsPanel) {
        tooltipsPanel.attachTooltips(self());
        self().getTraits().stream().filter(IFancyTooltip.class::isInstance).map(IFancyTooltip.class::cast)
                .forEach(tooltipsPanel::attachTooltips);
    }

    @Override
    default List<Component> getTabTooltips() {
        var list = new ArrayList<Component>();
        list.add(Component.translatable(self().getDefinition().getDescriptionId()));
        return list;
    }

    @Override
    default Component getTitle() {
        return Component.translatable(self().getDefinition().getDescriptionId());
    }
}
