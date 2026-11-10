package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblockpro.PartCells;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import org.jetbrains.annotations.Nullable;

/**
 * 在世界中标出多方块里可放部件的格子：绿框是仍为外壳、可换成部件的格子，蓝框是已放部件的格子。
 */
@OnlyIn(Dist.CLIENT)
public final class PartCellsOverlay {

    private static final int CHECK_INTERVAL = 10;
    private static final int UNFORMED_LIMIT = 100;
    private static final int POLL_PER_TICK = 64;
    private static final double MAX_DISTANCE_SQR = 192 * 192;
    private static final int OPEN_COLOR = 0x55FF55;
    private static final int FILLED_COLOR = 0x55AAFF;
    private static final float OPEN_FACE_ALPHA = 0.2f;
    private static final float FILLED_FACE_ALPHA = 0.1f;
    private static final CellBoxes BOXES = new CellBoxes(0.35f, 2f);
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    @Nullable
    private static Level world;
    @Nullable
    private static BlockPos anchor;
    @Nullable
    private static MachineDefinition definition;
    @Nullable
    private static long[] cells;
    private static boolean[] filled = new boolean[0];
    private static BlockState[] last = new BlockState[0];
    private static int cursor;
    private static int ticks;
    private static int unformedTicks;
    private static boolean dirty;

    private PartCellsOverlay() {}

    public static void show(PartCells.Result result) {
        clear();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var machine = MetaMachine.getMachine(level, result.controller());
        world = level;
        anchor = result.controller();
        definition = machine == null ? null : machine.getDefinition();
        var open = result.open();
        var installed = result.filled();
        int size = open.length + installed.length;
        cells = new long[size];
        filled = new boolean[size];
        last = new BlockState[size];
        System.arraycopy(open, 0, cells, 0, open.length);
        System.arraycopy(installed, 0, cells, open.length, installed.length);
        for (int i = open.length; i < size; i++) filled[i] = true;
        cursor = 0;
        ticks = 0;
        unformedTicks = 0;
        dirty = true;
    }

    public static boolean isShowing(BlockPos pos) {
        return cells != null && pos.equals(anchor);
    }

    public static void clear() {
        BOXES.close();
        world = null;
        anchor = null;
        definition = null;
        cells = null;
        filled = new boolean[0];
        last = new BlockState[0];
        dirty = false;
    }

    public static void tick() {
        var cells = PartCellsOverlay.cells;
        if (cells == null) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;
        var anchor = PartCellsOverlay.anchor;
        if (level == null || level != world || player == null || anchor == null ||
                player.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            clear();
            return;
        }
        if (++ticks % CHECK_INTERVAL == 0 && level.isLoaded(anchor)) {
            if (!(MetaMachine.getMachine(level, anchor) instanceof IMultiController controller) || controller.self().getDefinition() != definition) {
                clear();
                return;
            }
            unformedTicks = controller.isFormed() ? 0 : unformedTicks + CHECK_INTERVAL;
            if (unformedTicks >= UNFORMED_LIMIT) {
                clear();
                return;
            }
        }
        int size = cells.length;
        for (int k = Math.min(size, POLL_PER_TICK); k > 0; k--) {
            int i = cursor;
            cursor = cursor + 1 == size ? 0 : cursor + 1;
            CURSOR.set(cells[i]);
            if (!level.isLoaded(CURSOR)) continue;
            var state = level.getBlockState(CURSOR);
            var previous = last[i];
            if (state == previous) continue;
            last[i] = state;
            if (previous == null) continue;
            boolean part = MetaMachine.getMachine(level, CURSOR) instanceof IMultiPart;
            if (part != filled[i]) {
                filled[i] = part;
                dirty = true;
            }
        }
    }

    public static void render(RenderLevelStageEvent event) {
        var anchor = PartCellsOverlay.anchor;
        var cells = PartCellsOverlay.cells;
        if (cells == null || anchor == null) return;
        if (dirty) {
            dirty = false;
            var colors = new int[cells.length];
            var alphas = new float[cells.length];
            for (int i = 0; i < cells.length; i++) {
                colors[i] = filled[i] ? FILLED_COLOR : OPEN_COLOR;
                alphas[i] = filled[i] ? FILLED_FACE_ALPHA : OPEN_FACE_ALPHA;
            }
            BOXES.upload(anchor, cells, colors, alphas);
        }
        BOXES.draw(event, anchor);
    }
}
