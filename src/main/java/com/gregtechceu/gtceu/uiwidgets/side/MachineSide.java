package com.gregtechceu.gtceu.uiwidgets.side;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.uipro.styletemplate.WidgetIconAtlas;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.core.Direction;

public enum MachineSide {

    FRONT("gtceu.direction.tooltip.front", 1, 0),
    BACK("gtceu.direction.tooltip.back", 1, 1),
    LEFT("gtceu.direction.tooltip.left", 0, 2),
    RIGHT("gtceu.direction.tooltip.right", 0, 3),
    TOP("gtceu.direction.tooltip.up", 0, 0),
    BOTTOM("gtceu.direction.tooltip.down", 0, 1);

    private static final MachineSide[] VALUES = values();
    private static final WidgetIconAtlas ATLAS = new WidgetIconAtlas(GTCEu.id("textures/gui/uiwidgets/direction_icons.png"), 2);
    private static final IGuiTexture[] ICONS = new IGuiTexture[VALUES.length];

    static {
        for (var side : VALUES) ICONS[side.ordinal()] = ATLAS.pixelIcon(side.row, side.column);
    }

    public final String translationKey;
    private final int row, column;

    MachineSide(String translationKey, int row, int column) {
        this.translationKey = translationKey;
        this.row = row;
        this.column = column;
    }

    public IGuiTexture icon() {
        return ICONS[ordinal()];
    }

    public Direction toDirection(MetaMachine machine) {
        return frame(machine)[ordinal()];
    }

    public static MachineSide of(MetaMachine machine, Direction direction) {
        var frame = frame(machine);
        for (var side : VALUES) if (frame[side.ordinal()] == direction) return side;
        return FRONT;
    }

    private static Direction[] frame(MetaMachine machine) {
        Direction front = machine.hasFrontFacing() ? machine.getFrontFacing() : Direction.NORTH;
        Direction up = front.getAxis().isHorizontal() ? Direction.UP : MetaMachine.getUpwardFacing(machine);
        var cross = front.getNormal().cross(up.getNormal());
        Direction left = Direction.fromDelta(cross.getX(), cross.getY(), cross.getZ());
        if (left == null) left = front.getAxis().isHorizontal() ? front.getClockWise() : Direction.WEST;
        return new Direction[] { front, front.getOpposite(), left, left.getOpposite(), up, up.getOpposite() };
    }
}
