package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblockpro.PartCells;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 在世界中标出多方块里可放部件的格子：绿框是仍为外壳、可换成部件的格子，蓝框是已放部件的格子。
 */
@OnlyIn(Dist.CLIENT)
public final class PartCellsOverlay {

    private static final int CHECK_INTERVAL = 10;
    private static final int UNFORMED_LIMIT = 100;
    private static final int POLL_PER_TICK = 64;
    private static final double MAX_DISTANCE_SQR = 192 * 192;
    private static final float EXPAND = 0.01f;
    private static final float SEE_THROUGH = 0.35f;
    private static final float LINE_WIDTH = 2f;
    private static final int OPEN_COLOR = 0x55FF55;
    private static final int FILLED_COLOR = 0x55AAFF;
    private static final float OPEN_FACE_ALPHA = 0.2f;
    private static final float FILLED_FACE_ALPHA = 0.1f;
    private static final float LINE_ALPHA = 0.9f;
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
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
    @Nullable
    private static VertexBuffer faces;
    @Nullable
    private static VertexBuffer lines;

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
        if (faces != null) faces.close();
        if (lines != null) lines.close();
        faces = null;
        lines = null;
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
        if (cells == null || anchor == null) return;
        if (dirty) rebuild(anchor);
        var faces = PartCellsOverlay.faces;
        var lines = PartCellsOverlay.lines;
        var shader = GameRenderer.getPositionColorShader();
        if (faces == null || lines == null || shader == null) return;
        var camera = event.getCamera().getPosition();
        MODEL_VIEW.set(event.getPoseStack().last().pose()).translate((float) (anchor.getX() - camera.x), (float) (anchor.getY() - camera.y),
                (float) (anchor.getZ() - camera.z));
        var projection = event.getProjectionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, SEE_THROUGH);
        lines.bind();
        lines.drawWithShader(MODEL_VIEW, projection, shader);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        faces.bind();
        faces.drawWithShader(MODEL_VIEW, projection, shader);
        lines.bind();
        lines.drawWithShader(MODEL_VIEW, projection, shader);
        VertexBuffer.unbind();
        RenderSystem.lineWidth(1);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void rebuild(BlockPos anchor) {
        dirty = false;
        var cells = PartCellsOverlay.cells;
        if (cells == null) return;
        if (faces == null) faces = new VertexBuffer(VertexBuffer.Usage.STATIC);
        if (lines == null) lines = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < cells.length; i++) {
            int color = filled[i] ? FILLED_COLOR : OPEN_COLOR;
            float alpha = filled[i] ? FILLED_FACE_ALPHA : OPEN_FACE_ALPHA;
            box(buffer, cells[i], anchor, color, alpha);
        }
        faces.bind();
        faces.upload(buffer.end());
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < cells.length; i++) {
            edges(buffer, cells[i], anchor, filled[i] ? FILLED_COLOR : OPEN_COLOR);
        }
        lines.bind();
        lines.upload(buffer.end());
        VertexBuffer.unbind();
    }

    private static void box(BufferBuilder buffer, long pos, BlockPos anchor, int color, float alpha) {
        float x0 = BlockPos.getX(pos) - anchor.getX() - EXPAND, y0 = BlockPos.getY(pos) - anchor.getY() - EXPAND,
                z0 = BlockPos.getZ(pos) - anchor.getZ() - EXPAND;
        float x1 = x0 + 1 + 2 * EXPAND, y1 = y0 + 1 + 2 * EXPAND, z1 = z0 + 1 + 2 * EXPAND;
        float r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        quad(buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
        quad(buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
        quad(buffer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, alpha);
        quad(buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        quad(buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, alpha);
        quad(buffer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, alpha);
    }

    private static void quad(BufferBuilder buffer, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float r, float g, float b, float a) {
        buffer.vertex(ax, ay, az).color(r, g, b, a).endVertex();
        buffer.vertex(bx, by, bz).color(r, g, b, a).endVertex();
        buffer.vertex(cx, cy, cz).color(r, g, b, a).endVertex();
        buffer.vertex(dx, dy, dz).color(r, g, b, a).endVertex();
    }

    private static void edges(BufferBuilder buffer, long pos, BlockPos anchor, int color) {
        float x0 = BlockPos.getX(pos) - anchor.getX() - EXPAND, y0 = BlockPos.getY(pos) - anchor.getY() - EXPAND,
                z0 = BlockPos.getZ(pos) - anchor.getZ() - EXPAND;
        float x1 = x0 + 1 + 2 * EXPAND, y1 = y0 + 1 + 2 * EXPAND, z1 = z0 + 1 + 2 * EXPAND;
        float r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        line(buffer, x0, y0, z0, x1, y0, z0, r, g, b);
        line(buffer, x0, y0, z1, x1, y0, z1, r, g, b);
        line(buffer, x0, y1, z0, x1, y1, z0, r, g, b);
        line(buffer, x0, y1, z1, x1, y1, z1, r, g, b);
        line(buffer, x0, y0, z0, x0, y1, z0, r, g, b);
        line(buffer, x1, y0, z0, x1, y1, z0, r, g, b);
        line(buffer, x0, y0, z1, x0, y1, z1, r, g, b);
        line(buffer, x1, y0, z1, x1, y1, z1, r, g, b);
        line(buffer, x0, y0, z0, x0, y0, z1, r, g, b);
        line(buffer, x1, y0, z0, x1, y0, z1, r, g, b);
        line(buffer, x0, y1, z0, x0, y1, z1, r, g, b);
        line(buffer, x1, y1, z0, x1, y1, z1, r, g, b);
    }

    private static void line(BufferBuilder buffer, float ax, float ay, float az, float bx, float by, float bz, float r, float g, float b) {
        buffer.vertex(ax, ay, az).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(bx, by, bz).color(r, g, b, LINE_ALPHA).endVertex();
    }
}
