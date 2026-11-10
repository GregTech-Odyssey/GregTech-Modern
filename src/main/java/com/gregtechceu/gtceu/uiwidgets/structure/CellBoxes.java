package com.gregtechceu.gtceu.uiwidgets.structure;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 世界中按格画半透明方框（面 + 透视描边），两块 VBO 只在 {@link #upload} 时重建，每帧零分配。
 */
@OnlyIn(Dist.CLIENT)
final class CellBoxes {

    private static final float EXPAND = 0.01f;
    private static final float LINE_ALPHA = 0.9f;
    private static final Matrix4f MODEL_VIEW = new Matrix4f();

    private final float seeThrough;
    private final float lineWidth;
    @Nullable
    private VertexBuffer faces;
    @Nullable
    private VertexBuffer lines;

    CellBoxes(float seeThrough, float lineWidth) {
        this.seeThrough = seeThrough;
        this.lineWidth = lineWidth;
    }

    void upload(BlockPos anchor, long[] cells, int[] colors, float[] faceAlphas) {
        if (faces == null) faces = new VertexBuffer(VertexBuffer.Usage.STATIC);
        if (lines == null) lines = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        var marked = new LongOpenHashSet(cells);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < cells.length; i++) box(buffer, cells[i], anchor, colors[i], faceAlphas[i], marked);
        faces.bind();
        faces.upload(buffer.end());
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < cells.length; i++) edges(buffer, cells[i], anchor, colors[i]);
        lines.bind();
        lines.upload(buffer.end());
        VertexBuffer.unbind();
    }

    void draw(RenderLevelStageEvent event, BlockPos anchor) {
        var faces = this.faces;
        var lines = this.lines;
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
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, seeThrough);
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

    void close() {
        if (faces != null) faces.close();
        if (lines != null) lines.close();
        faces = null;
        lines = null;
    }

    private static void box(BufferBuilder buffer, long pos, BlockPos anchor, int color, float alpha, LongOpenHashSet marked) {
        float x0 = BlockPos.getX(pos) - anchor.getX() - EXPAND, y0 = BlockPos.getY(pos) - anchor.getY() - EXPAND,
                z0 = BlockPos.getZ(pos) - anchor.getZ() - EXPAND;
        float x1 = x0 + 1 + 2 * EXPAND, y1 = y0 + 1 + 2 * EXPAND, z1 = z0 + 1 + 2 * EXPAND;
        float r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        if (!marked.contains(BlockPos.offset(pos, 0, -1, 0))) quad(buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
        if (!marked.contains(BlockPos.offset(pos, 0, 1, 0))) quad(buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
        if (!marked.contains(BlockPos.offset(pos, 0, 0, -1))) quad(buffer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, alpha);
        if (!marked.contains(BlockPos.offset(pos, 0, 0, 1))) quad(buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        if (!marked.contains(BlockPos.offset(pos, -1, 0, 0))) quad(buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, alpha);
        if (!marked.contains(BlockPos.offset(pos, 1, 0, 0))) quad(buffer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, alpha);
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
