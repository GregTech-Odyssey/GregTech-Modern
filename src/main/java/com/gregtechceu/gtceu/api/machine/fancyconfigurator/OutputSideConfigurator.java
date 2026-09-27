package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.side.MachineSide;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.AlignItems;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class OutputSideConfigurator implements IFancyConfigurator {

    private static final String TITLE = "gtceu.gui.output_setting.title";
    private static final String ITEMS = "gtceu.gui.output_side.items";
    private static final String FLUIDS = "gtceu.gui.output_side.fluids";
    private static final String FACE = "gtceu.gui.output_side.face";
    private static final String ITEM_CURRENT = "gtceu.gui.output_side.item_current";
    private static final String FLUID_CURRENT = "gtceu.gui.output_side.fluid_current";
    private static final String SET_ITEM = "gtceu.gui.output_side.set_item";
    private static final String SET_FLUID = "gtceu.gui.output_side.set_fluid";
    private static final String FRONT = "gtceu.gui.output_side.front";
    private static final String NO_FACE = "gtceu.gui.output_side.no_face";
    private static final String AUTO = "gtceu.gui.output_side.auto";
    private static final String AUTO_TOOLTIP = "gtceu.gui.output_side.auto.tooltip";
    private static final String ALLOW_INPUT = "gtceu.gui.output_side.allow_input";
    private static final String ALLOW_INPUT_TOOLTIP = "gtceu.gui.output_side.allow_input.tooltip";
    private static final int FACE_BUTTON = UISizes.SLOT + 2;
    private static final int CONTENT_WIDTH = 6 * UISizes.SLOT;

    private final MetaMachine machine;
    @Nullable
    private final IAutoOutputItem items;
    @Nullable
    private final IAutoOutputFluid fluids;

    private OutputSideConfigurator(MetaMachine machine, @Nullable IAutoOutputItem items, @Nullable IAutoOutputFluid fluids) {
        this.machine = machine;
        this.items = items;
        this.fluids = fluids;
    }

    @Nullable
    public static OutputSideConfigurator of(MetaMachine machine) {
        var items = machine instanceof IAutoOutputItem item && item.hasAutoOutputItem() ? item : null;
        var fluids = machine instanceof IAutoOutputFluid fluid && fluid.hasAutoOutputFluid() ? fluid : null;
        return items == null && fluids == null ? null : new OutputSideConfigurator(machine, items, fluids);
    }

    public static void attach(ConfiguratorPanel panel, IFancyUIProvider provider) {
        if (!(provider instanceof MetaMachine machine)) return;
        var configurator = of(machine);
        if (configurator != null) panel.attachConfigurators(configurator);
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE);
    }

    @Override
    public IGuiTexture getIcon() {
        return WidgetIcons.EXPORT;
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(getTitle());
    }

    @Override
    public Widget createConfigurator() {
        var channel = new Channel(items == null);
        var page = UIElement.column(LayoutStyle.AUTO).layout(l -> l.minWidth(CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
        if (items != null && fluids != null) {
            page.addChild(ButtonGroup.single(2, i -> Component.translatable(i == 0 ? ITEMS : FLUIDS),
                    () -> channel.fluid ? 1 : 0, i -> channel.fluid = i == 1).horizontal());
        }
        page.addChild(faceGrid(channel));
        page.addChild(CoverUIs.controlRow(AUTO, Switch.of(() -> isAuto(channel), on -> setAuto(channel, on))
                .disabled(() -> facing(channel) == null, NO_FACE), AUTO_TOOLTIP));
        page.addChild(CoverUIs.controlRow(ALLOW_INPUT, Switch.of(() -> isAllowInput(channel), on -> setAllowInput(channel, on))
                .disabled(() -> facing(channel) == null, NO_FACE), ALLOW_INPUT_TOOLTIP));
        return page;
    }

    private UIElement faceGrid(Channel channel) {
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP).alignSelf(AlignItems.CENTER))
                .addChildren(gridRow(channel, null, MachineSide.TOP, null),
                        gridRow(channel, MachineSide.LEFT, MachineSide.FRONT, MachineSide.RIGHT),
                        gridRow(channel, null, MachineSide.BOTTOM, MachineSide.BACK));
    }

    private UIElement gridRow(Channel channel, @Nullable MachineSide left, MachineSide middle, @Nullable MachineSide right) {
        return UIElement.row(FACE_BUTTON).layout(l -> l.gapAll(UISizes.GAP))
                .addChildren(faceButton(channel, left), faceButton(channel, middle), faceButton(channel, right));
    }

    private Widget faceButton(Channel channel, @Nullable MachineSide side) {
        if (side == null) return UIElement.spacer(FACE_BUTTON, FACE_BUTTON);
        var button = new FaceButton(side.icon(), () -> facing(channel) == side.toDirection(machine), on -> toggleFace(channel, side));
        button.bindTooltip(() -> faceTooltip(channel, side));
        button.disabled(() -> isFront(side.toDirection(machine)), FRONT);
        return button;
    }

    private Component faceTooltip(Channel channel, MachineSide side) {
        var direction = side.toDirection(machine);
        var name = Component.translatable(FACE, Component.translatable(side.translationKey), Component.translatable(switch (direction) {
            case UP -> "gtceu.gui.output_side.dir.up";
            case DOWN -> "gtceu.gui.output_side.dir.down";
            case NORTH -> "gtceu.gui.output_side.dir.north";
            case SOUTH -> "gtceu.gui.output_side.dir.south";
            case WEST -> "gtceu.gui.output_side.dir.west";
            case EAST -> "gtceu.gui.output_side.dir.east";
        }));
        boolean current = facing(channel) == direction;
        if (channel.fluid) return Component.translatable(current ? FLUID_CURRENT : SET_FLUID, name);
        return Component.translatable(current ? ITEM_CURRENT : SET_ITEM, name);
    }

    private boolean isFront(Direction direction) {
        return machine.hasFrontFacing() && machine.getFrontFacing() == direction;
    }

    @Nullable
    private Direction facing(Channel channel) {
        if (channel.fluid) return fluids == null ? null : fluids.getOutputFacingFluids();
        return items == null ? null : items.getOutputFacingItems();
    }

    private void toggleFace(Channel channel, MachineSide side) {
        var direction = side.toDirection(machine);
        if (isFront(direction)) return;
        var target = facing(channel) == direction ? null : direction;
        if (channel.fluid) {
            if (fluids != null) fluids.setOutputFacingFluids(target);
        } else if (items != null) {
            items.setOutputFacingItems(target);
        }
        machine.requestSync();
    }

    private boolean isAuto(Channel channel) {
        if (channel.fluid) return fluids != null && fluids.isAutoOutputFluids();
        return items != null && items.isAutoOutputItems();
    }

    private void setAuto(Channel channel, boolean on) {
        if (channel.fluid) {
            if (fluids != null) fluids.setAutoOutputFluids(on);
        } else if (items != null) {
            items.setAutoOutputItems(on);
        }
        machine.requestSync();
    }

    private boolean isAllowInput(Channel channel) {
        if (channel.fluid) return fluids != null && fluids.isAllowInputFromOutputSideFluids();
        return items != null && items.isAllowInputFromOutputSideItems();
    }

    private void setAllowInput(Channel channel, boolean on) {
        if (channel.fluid) {
            if (fluids != null) fluids.setAllowInputFromOutputSideFluids(on);
        } else if (items != null) {
            items.setAllowInputFromOutputSideItems(on);
        }
        machine.requestSync();
    }

    private static final class Channel {

        private boolean fluid;

        private Channel(boolean fluid) {
            this.fluid = fluid;
        }
    }

    private static final class FaceButton extends IconToggle {

        private FaceButton(IGuiTexture icon, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(icon, FACE_BUTTON, getter, setter);
        }
    }
}
