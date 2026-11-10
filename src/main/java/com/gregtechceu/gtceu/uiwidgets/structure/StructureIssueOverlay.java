package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureIssue;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * 在世界中用红框标出多方块结构问题所在的格子；结构成型、远离或超时后自动消失。
 */
@OnlyIn(Dist.CLIENT)
public final class StructureIssueOverlay {

    private static final int COLOR = 0xFF5555;
    private static final float FACE_ALPHA = 0.08f;
    private static final int CHECK_INTERVAL = 10;
    private static final int LIFETIME = 20 * 60;
    private static final int GROUP_LIFETIME = 20 * 60 * 5;
    private static final double MAX_DISTANCE_SQR = 192 * 192;
    private static final CellBoxes BOXES = new CellBoxes(0.35f, 3f);
    private static final long[] NONE = new long[0];

    @Nullable
    private static Level world;
    @Nullable
    private static BlockPos controller;
    private static long[] cells = NONE;
    private static int lifetime;
    private static int ticks;
    private static boolean dirty;

    private StructureIssueOverlay() {}

    public static void show(BlockPos controllerPos, long pos) {
        boolean grouped = controllerPos.equals(controller) && lifetime == GROUP_LIFETIME;
        if (pos == StructureIssue.NO_POS) {
            if (controllerPos.equals(controller) && !grouped) clear();
            return;
        }
        if (!grouped) show(controllerPos, new long[] { pos }, LIFETIME);
    }

    public static void showAll(BlockPos controllerPos, long[] positions) {
        if (positions.length == 0) {
            if (controllerPos.equals(controller)) clear();
            return;
        }
        show(controllerPos, positions.clone(), GROUP_LIFETIME);
    }

    public static boolean isShowing(BlockPos controllerPos, long[] positions) {
        return controllerPos.equals(controller) && Arrays.equals(cells, positions);
    }

    private static void show(BlockPos controllerPos, long[] positions, int life) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ticks = 0;
        lifetime = life;
        if (level == world && controllerPos.equals(controller) && Arrays.equals(cells, positions)) return;
        world = level;
        controller = controllerPos.immutable();
        cells = positions;
        dirty = true;
    }

    public static void clear() {
        BOXES.close();
        world = null;
        controller = null;
        cells = NONE;
        lifetime = 0;
        dirty = false;
    }

    public static void tick() {
        var controller = StructureIssueOverlay.controller;
        if (controller == null) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;
        if (level == null || level != world || player == null || ++ticks > lifetime ||
                player.distanceToSqr(controller.getX() + 0.5, controller.getY() + 0.5, controller.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            clear();
            return;
        }
        if (ticks % CHECK_INTERVAL == 0 && level.isLoaded(controller) &&
                (!(MetaMachine.getMachine(level, controller) instanceof IMultiController multi) || multi.isFormed())) {
            clear();
        }
    }

    public static void render(RenderLevelStageEvent event) {
        var anchor = controller;
        if (anchor == null || cells.length == 0) return;
        if (dirty) {
            dirty = false;
            var colors = new int[cells.length];
            var alphas = new float[cells.length];
            Arrays.fill(colors, COLOR);
            Arrays.fill(alphas, FACE_ALPHA);
            BOXES.upload(anchor, cells, colors, alphas);
        }
        BOXES.draw(event, anchor);
    }
}
