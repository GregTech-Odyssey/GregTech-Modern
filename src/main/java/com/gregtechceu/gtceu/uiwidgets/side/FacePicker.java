package com.gregtechceu.gtceu.uiwidgets.side;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.AlignItems;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FacePicker {

    public static final int BUTTON = UISizes.SLOT_SIZE + 2;
    private static final String SELECT = "gtceu.gui.face_picker.select";

    private FacePicker() {}

    public static UIElement grid(Supplier<Direction> current, Consumer<Direction> select) {
        var grid = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP).alignSelf(AlignItems.CENTER));
        var selected = grid.addSyncValue(SyncValue.ofInt(() -> {
            var face = current.get();
            return face == null ? -1 : face.get3DDataValue();
        }, -1));
        for (var row : FaceNet.LAYOUT) {
            var line = UIElement.row(BUTTON).layout(l -> l.gapAll(UISizes.GAP));
            for (var direction : row) line.addChild(button(direction, selected, select));
            grid.addChild(line);
        }
        return grid;
    }

    private static Widget button(@Nullable Direction direction, SyncValue<Integer> selected, Consumer<Direction> select) {
        if (direction == null) return UIElement.spacer(BUTTON, BUTTON);
        var name = Component.translatable(FaceNet.nameKey(direction));
        var shortName = Component.translatable(shortKey(direction));
        var button = Button.text(BUTTON, BUTTON, shortName)
                .bindClientVariant(() -> selected.getValue() == direction.get3DDataValue() ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT)
                .setOnServerClick(() -> select.accept(direction));
        button.tooltips(Component.translatable(SELECT, name));
        return button;
    }

    public static String shortKey(Direction direction) {
        return switch (direction) {
            case UP -> "gtceu.gui.face_picker.short.up";
            case DOWN -> "gtceu.gui.face_picker.short.down";
            case NORTH -> "gtceu.gui.face_picker.short.north";
            case SOUTH -> "gtceu.gui.face_picker.short.south";
            case WEST -> "gtceu.gui.face_picker.short.west";
            case EAST -> "gtceu.gui.face_picker.short.east";
        };
    }
}
