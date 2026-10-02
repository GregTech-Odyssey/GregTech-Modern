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
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[][] FRAMES = new Direction[DIRECTIONS.length * DIRECTIONS.length][];

    static {
        for (var side : VALUES) ICONS[side.ordinal()] = ATLAS.pixelIcon(side.row, side.column);
        for (var front : DIRECTIONS) {
            for (var up : DIRECTIONS) FRAMES[front.ordinal() * DIRECTIONS.length + up.ordinal()] = frame(front, up);
        }
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
        return FRAMES[frameKey(machine)][ordinal()];
    }

    public Direction toDirection(int frameKey) {
        return FRAMES[Math.floorMod(frameKey, FRAMES.length)][ordinal()];
    }

    public static MachineSide of(MetaMachine machine, Direction direction) {
        return of(frameKey(machine), direction);
    }

    public static MachineSide of(int frameKey, Direction direction) {
        var frame = FRAMES[Math.floorMod(frameKey, FRAMES.length)];
        for (var side : VALUES) if (frame[side.ordinal()] == direction) return side;
        return FRONT;
    }

    public static int frameKey(MetaMachine machine) {
        Direction front = machine.hasFrontFacing() ? machine.getFrontFacing() : Direction.NORTH;
        Direction up = front.getAxis().isHorizontal() ? Direction.UP : MetaMachine.getUpwardFacing(machine);
        return front.ordinal() * DIRECTIONS.length + up.ordinal();
    }

    private static Direction[] frame(Direction front, Direction up) {
        var cross = front.getNormal().cross(up.getNormal());
        Direction left = Direction.fromDelta(cross.getX(), cross.getY(), cross.getZ());
        if (left == null) left = front.getAxis().isHorizontal() ? front.getClockWise() : Direction.WEST;
        return new Direction[] { front, front.getOpposite(), left, left.getOpposite(), up, up.getOpposite() };
    }
}
