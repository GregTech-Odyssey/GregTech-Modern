package com.gregtechceu.gtceu.uiwidgets.side;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.IntSupplier;

public class FaceNet extends UIElement {

    public static final int SIZE = UISizes.SLOT;
    public static final int PASS_THROUGH = 1 << 6;
    public static final Direction[][] LAYOUT = {
            { null, Direction.NORTH, null },
            { Direction.WEST, Direction.UP, Direction.EAST },
            { null, Direction.SOUTH, Direction.DOWN } };

    private static final int CELL = 4;
    private static final int CELL_GAP = 2;
    private static final String PASS_THROUGH_KEY = "gtceu.gui.face_net.pass_through";
    private static final String PASS_THROUGH_DETAIL = "gtceu.gui.face_net.pass_through.detail";
    private static final String SINGLE = "gtceu.gui.face_net.single";
    private static final String NONE = "gtceu.gui.face_net.none";
    private static final String LEGEND = "gtceu.gui.face_net.legend";
    private static final String CLICK = "gtceu.gui.face_net.click";

    private final SyncValue<Integer> mask;
    @Nullable
    private Runnable onClientClick;

    public FaceNet(IntSupplier serverMask) {
        layout(l -> l.size(SIZE, SIZE));
        this.mask = addSyncValue(SyncValue.ofInt(serverMask::getAsInt, 0).onChanged(value -> applyTooltip()));
        applyTooltip();
    }

    public static int bit(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    public static int maskOf(@Nullable Direction face) {
        return face == null ? PASS_THROUGH : bit(face);
    }

    public static String nameKey(Direction direction) {
        return switch (direction) {
            case UP -> "gtceu.gui.output_side.dir.up";
            case DOWN -> "gtceu.gui.output_side.dir.down";
            case NORTH -> "gtceu.gui.output_side.dir.north";
            case SOUTH -> "gtceu.gui.output_side.dir.south";
            case WEST -> "gtceu.gui.output_side.dir.west";
            case EAST -> "gtceu.gui.output_side.dir.east";
        };
    }

    public FaceNet setOnClientClick(Runnable onClientClick) {
        this.onClientClick = onClientClick;
        applyTooltip();
        return this;
    }

    public int getMask() {
        return mask.getValue();
    }

    private void applyTooltip() {
        int value = mask == null ? 0 : mask.getValue();
        if (value == 0) {
            setHoverTooltips(new ArrayList<Component>());
            return;
        }
        var lines = new ArrayList<Component>(4);
        if ((value & PASS_THROUGH) != 0) {
            lines.add(Component.translatable(PASS_THROUGH_KEY));
            lines.add(Component.translatable(PASS_THROUGH_DETAIL).withStyle(ChatFormatting.GRAY));
        } else {
            Direction single = null;
            for (var direction : Direction.values()) {
                if ((value & bit(direction)) != 0) {
                    single = direction;
                    break;
                }
            }
            lines.add(single == null ? Component.translatable(NONE) : Component.translatable(SINGLE, Component.translatable(nameKey(single))));
        }
        lines.add(Component.translatable(LEGEND).withStyle(ChatFormatting.GRAY));
        if (onClientClick != null) lines.add(Component.translatable(CLICK).withStyle(ChatFormatting.YELLOW));
        setHoverTooltips(lines);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && onClientClick != null && mask.getValue() != 0 && isMouseOverElement(mouseX, mouseY)) {
            onClientClick.run();
            playButtonClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY();
        int value = mask.getValue();
        if (value != 0 && onClientClick != null && isMouseOverElement(mouseX, mouseY)) graphics.fill(x, y, x + SIZE, y + SIZE, UITheme.SEGMENT_HOVER);
        if (value == 0) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        boolean passThrough = (value & PASS_THROUGH) != 0;
        int origin = (SIZE - 3 * CELL - 2 * CELL_GAP) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                var direction = LAYOUT[row][column];
                if (direction == null) continue;
                int cx = x + origin + column * (CELL + CELL_GAP), cy = y + origin + row * (CELL + CELL_GAP);
                if (passThrough) {
                    drawCell(graphics, cx, cy, UITheme.FLOW_CYAN_LIGHT, UITheme.FLOW_CYAN_MID);
                } else if ((value & bit(direction)) != 0) {
                    drawCell(graphics, cx, cy, UITheme.FLOW_GREEN_LIGHT, UITheme.FLOW_GREEN_MID);
                } else {
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, UITheme.FACE_NET_OFF);
                }
            }
        }
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawCell(GuiGraphics graphics, int x, int y, int light, int mid) {
        graphics.fill(x, y, x + CELL, y + CELL, mid);
        graphics.fill(x, y, x + CELL - 1, y + 1, light);
        graphics.fill(x, y + 1, x + 1, y + CELL - 1, light);
    }
}
