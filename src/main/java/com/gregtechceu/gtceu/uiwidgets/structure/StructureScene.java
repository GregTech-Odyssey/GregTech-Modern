package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.uipro.view.scene.SceneView;

import com.lowdragmc.lowdraglib.client.utils.RenderUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public final class StructureScene extends SceneView {

    public static final int ALL_LAYERS = PreviewLevel.ALL_LAYERS;
    private static final float FIT_PER_SIZE = 5f;
    private static final float DEFAULT_PER_SIZE = 3.5f;
    private static final float SELECTION_RED = 0.6f;
    private static final float SELECTION_SCALE = 1.01f;
    private static final float HIGHLIGHT_INSET = -0.02f;
    private static final int PROGRESS_COLOR = 0xFF55C255;
    private static final int PROGRESS_TRACK = 0x40000000;
    private static final float WAIT_SHARE = 0.7f;
    private static final long ORBIT_TICKS = 180;
    private static final float ORBIT_DEGREES_PER_TICK = 2;
    private static final float ORBIT_ELEVATION = 25;
    private static final float FACE_OFFSET = 0.004f;
    private static final float FACE_FRAME = 0.07f;

    private final StructureRenderer renderer = new StructureRenderer();
    private final SceneMarkers markers = new SceneMarkers();
    private final PreviewBounds bounds = new PreviewBounds();
    @Nullable
    private PreviewLevel level;
    @Setter
    @Nullable
    private BiConsumer<BlockPos, Direction> onSelected;
    @Setter
    @Nullable
    private Runnable reloader;
    @Setter
    private DoubleSupplier waiting = () -> -1;
    @Setter
    @Nullable
    private Consumer<Marker> onMarker;
    private float renderStart;
    @Nullable
    private Long2ObjectOpenHashMap<BlockState> pendingBlocks;
    @Nullable
    private Long2ObjectOpenHashMap<BlockEntity> pendingEntities;
    private int pendingLayer = ALL_LAYERS;
    private float pendingZoom = -1;
    private boolean hasPending;
    @Nullable
    private BlockPos hoverPos;
    @Nullable
    private Direction hoverFace;
    @Nullable
    private BlockPos selectedPos;
    private ItemStack hoverItem = ItemStack.EMPTY;
    private final List<Overlay> overlays = new ArrayList<>();
    private LongArrayList highlight = new LongArrayList();
    private int highlightColor;
    @Setter
    private boolean autoOrbit;
    @Setter
    private boolean selectable = true;
    @Setter
    private boolean selectionBox = true;
    @Setter
    @Nullable
    private FacePainter facePainter;
    @Setter
    @Nullable
    private Supplier<List<Component>> tooltip;
    @Nullable
    private BufferBuilder faceBuffer;
    private final FaceSink faceSink = this::addFace;

    public interface FacePainter {

        void paint(FaceSink sink);
    }

    public interface FaceSink {

        void face(BlockPos pos, Direction face, int frameColor, int fillColor, float inset);
    }

    public record Marker(Vector3f pos, int color, boolean selected, List<Component> tooltip) {}

    private static final class Overlay {

        final int id;
        final StructureRenderer renderer = new StructureRenderer();
        @Nullable
        PreviewLevel level;
        float alpha = 1;

        Overlay(int id) {
            this.id = id;
        }
    }

    public StructureScene(int width, int height, boolean movable) {
        this("structure_scene", width, height, movable);
    }

    public StructureScene(String id, int width, int height, boolean movable) {
        super(id, width, height, movable);
    }

    public static float fitZoom(int width, int height, int depth) {
        return fitZoom(Math.max(width, Math.max(height, depth)), FIT_PER_SIZE);
    }

    private static float fitZoom(int size, float perSize) {
        return perSize * (float) Math.sqrt(Math.max(size, 1));
    }

    @Override
    protected void idle() {
        if (reloader != null) dispose();
        else releaseGpu();
    }

    public void setOverlay(int id, @Nullable Long2ObjectOpenHashMap<BlockState> blocks, float r, float g, float b, float a) {
        Overlay overlay = null;
        for (var candidate : overlays) {
            if (candidate.id == id) overlay = candidate;
        }
        if (blocks == null || blocks.isEmpty()) {
            if (overlay != null) {
                overlay.renderer.dispose();
                if (overlay.level != null) overlay.level.clear();
                overlays.remove(overlay);
            }
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (overlay == null) {
            overlay = new Overlay(id);
            overlays.add(overlay);
        }
        if (overlay.level == null) overlay.level = new PreviewLevel(minecraft.level);
        overlay.level.setBlocks(blocks);
        overlay.alpha = a;
        overlay.renderer.tint(r, g, b, a);
        overlay.renderer.show(overlay.level.view(), true, camera.center());
        overlays.sort((x, y) -> Float.compare(y.alpha, x.alpha));
    }

    public void setHighlight(@Nullable LongArrayList positions, int argb) {
        this.highlight = positions == null ? new LongArrayList() : positions;
        this.highlightColor = argb;
    }

    public void setMarkers(List<Marker> markers) {
        this.markers.set(markers);
    }

    public void show(@Nullable Long2ObjectOpenHashMap<BlockState> blocks, int onlyY, float zoom) {
        if (blocks != null) {
            pendingBlocks = blocks;
            pendingEntities = null;
        }
        pendingLayer = onlyY;
        if (zoom > 0) pendingZoom = zoom;
        hasPending = true;
    }

    public void showLive(Long2ObjectOpenHashMap<BlockState> blocks, Long2ObjectOpenHashMap<BlockEntity> entities, float zoom) {
        show(blocks, ALL_LAYERS, zoom);
        pendingEntities = entities;
    }

    @Nullable
    public BlockPos getHoverPos() {
        return hoverPos;
    }

    @Nullable
    public Direction getHoverFace() {
        return hoverFace;
    }

    public void releaseGpu() {
        renderer.releaseGpu();
        for (var overlay : overlays) overlay.renderer.releaseGpu();
    }

    public void cancelRender() {
        renderer.cancel();
    }

    public void dispose() {
        renderer.dispose();
        for (var overlay : overlays) {
            overlay.renderer.dispose();
            if (overlay.level != null) overlay.level.clear();
        }
        overlays.clear();
        if (level != null) level.clear();
        level = null;
        hoverPos = selectedPos = null;
        hoverItem = ItemStack.EMPTY;
    }

    private void applyPending() {
        if (!hasPending) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        hasPending = false;
        boolean changed = pendingBlocks != null;
        if (changed) {
            if (level == null) level = new PreviewLevel(minecraft.level);
            if (pendingEntities != null) level.setLiveBlocks(pendingBlocks, pendingEntities);
            else level.setBlocks(pendingBlocks);
            pendingBlocks = null;
            pendingEntities = null;
            hoverPos = selectedPos = null;
            hoverItem = ItemStack.EMPTY;
        }
        if (level == null) return;
        level.setLayer(pendingLayer);
        var view = level.view();
        if ((changed || !camera.hasHome()) && !view.states.isEmpty()) {
            bounds.measure(view.states);
            float cx = (bounds.minX + bounds.maxX) / 2f + 0.5f, cz = (bounds.minZ + bounds.maxZ) / 2f + 0.5f;
            float cy = view.onlyY == ALL_LAYERS ? (bounds.minY + bounds.maxY) / 2f + 0.5f : view.onlyY + 0.5f;
            int size = Math.max(Math.max(bounds.width(), view.onlyY == ALL_LAYERS ? bounds.height() : 1), bounds.depth());
            camera.place(new Vector3f(cx, cy, cz), pendingZoom > 0 ? pendingZoom : fitZoom(size, DEFAULT_PER_SIZE));
        }
        pendingZoom = -1;
        renderer.show(view, changed, camera.center());
    }

    @Override
    protected float reach() {
        if (bounds.empty) return camera.zoom();
        var eye = camera.eye();
        float dx = Math.max(Math.max(bounds.minX - eye.x(), eye.x() - bounds.maxX - 1), 0);
        float dy = Math.max(Math.max(bounds.minY - eye.y(), eye.y() - bounds.maxY - 1), 0);
        float dz = Math.max(Math.max(bounds.minZ - eye.z(), eye.z() - bounds.maxZ - 1), 0);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    protected boolean hasContent() {
        return renderer.hasData();
    }

    @Override
    protected void beforeRender() {
        if (!hasPending && !renderer.hasData() && reloader != null) reloader.run();
        applyPending();
        if (autoOrbit) {
            var minecraft = Minecraft.getInstance();
            float ticks = (minecraft.level == null ? 0 : minecraft.level.getGameTime() % ORBIT_TICKS) + minecraft.getFrameTime();
            camera.orbit(ticks * ORBIT_DEGREES_PER_TICK, ORBIT_ELEVATION);
        }
        if (!renderer.hasData()) return;
        renderer.upload();
        for (var overlay : overlays) overlay.renderer.upload();
    }

    @Override
    protected void renderContent(FrustumIntersection frustum, Vector3f eye, float partialTicks) {
        renderer.render(frustum, eye, partialTicks);
        for (var overlay : overlays) overlay.renderer.render(frustum, eye, partialTicks);
    }

    @Override
    protected void renderExtras(float partialTicks) {
        if (!highlight.isEmpty()) renderHighlight();
        if (facePainter != null) renderFaces(facePainter);
        if (selectionBox && selectedPos != null) RenderUtils.renderBlockOverLay(new PoseStack(), selectedPos, SELECTION_RED, 0, 0, SELECTION_SCALE);
    }

    private void renderFaces(FacePainter painter) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        faceBuffer = Tesselator.getInstance().getBuilder();
        faceBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        painter.paint(faceSink);
        faceBuffer = null;
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private void addFace(BlockPos pos, Direction face, int frameColor, int fillColor, float inset) {
        var buffer = faceBuffer;
        if (buffer == null) return;
        float lo = inset, hi = 1 - inset;
        if ((fillColor >>> 24) != 0) faceQuad(buffer, pos, face, lo, lo, hi, hi, fillColor);
        if ((frameColor >>> 24) == 0) return;
        float in = Math.min(FACE_FRAME, (hi - lo) / 2);
        faceQuad(buffer, pos, face, lo, lo, hi, lo + in, frameColor);
        faceQuad(buffer, pos, face, lo, hi - in, hi, hi, frameColor);
        faceQuad(buffer, pos, face, lo, lo + in, lo + in, hi - in, frameColor);
        faceQuad(buffer, pos, face, hi - in, lo + in, hi, hi - in, frameColor);
    }

    private static void faceQuad(BufferBuilder buffer, BlockPos pos, Direction face, float u0, float v0, float u1, float v1, int argb) {
        float a = (argb >>> 24) / 255f, r = (argb >> 16 & 0xFF) / 255f, g = (argb >> 8 & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float plane = positive ? 1 + FACE_OFFSET : -FACE_OFFSET;
        faceVertex(buffer, pos, face.getAxis(), plane, u0, v0, r, g, b, a);
        faceVertex(buffer, pos, face.getAxis(), plane, u1, v0, r, g, b, a);
        faceVertex(buffer, pos, face.getAxis(), plane, u1, v1, r, g, b, a);
        faceVertex(buffer, pos, face.getAxis(), plane, u0, v1, r, g, b, a);
    }

    private static void faceVertex(BufferBuilder buffer, BlockPos pos, Direction.Axis axis, float plane, float u, float v, float r, float g,
                                   float b, float a) {
        float x, y, z;
        switch (axis) {
            case X -> {
                x = plane;
                y = u;
                z = v;
            }
            case Y -> {
                x = u;
                y = plane;
                z = v;
            }
            default -> {
                x = u;
                y = v;
                z = plane;
            }
        }
        buffer.vertex(pos.getX() + x, pos.getY() + y, pos.getZ() + z).color(r, g, b, a).endVertex();
    }

    private void renderHighlight() {
        float a = (highlightColor >>> 24) / 255f, r = (highlightColor >> 16 & 0xFF) / 255f, g = (highlightColor >> 8 & 0xFF) / 255f,
                b = (highlightColor & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int onlyY = level == null ? ALL_LAYERS : level.view().onlyY;
        for (int i = 0; i < highlight.size(); i++) {
            long pos = highlight.getLong(i);
            if (onlyY != ALL_LAYERS && BlockPos.getY(pos) != onlyY) continue;
            float x0 = BlockPos.getX(pos) + HIGHLIGHT_INSET, y0 = BlockPos.getY(pos) + HIGHLIGHT_INSET, z0 = BlockPos.getZ(pos) + HIGHLIGHT_INSET;
            float x1 = x0 + 1 - 2 * HIGHLIGHT_INSET, y1 = y0 + 1 - 2 * HIGHLIGHT_INSET, z1 = z0 + 1 - 2 * HIGHLIGHT_INSET;
            box(buffer, x0, y0, z0, x1, y1, z1, r, g, b, a);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void box(BufferBuilder buffer, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
        buffer.vertex(x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x1, y0, z1).color(r, g, b, a).endVertex();
    }

    @Override
    protected void drawOverScene(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (hasContent() && hasMatrix) {
            markers.draw(graphics, combined, camera.eye(), level == null ? null : level.view(), bounds, getPositionX(), getPositionY(),
                    getSizeWidth(), getSizeHeight(), !isInteracting() && isPointerOver(mouseX, mouseY), mouseX, mouseY);
        }
        double wait = waiting.getAsDouble();
        if (wait >= 0) {
            drawProgress(graphics, WAIT_SHARE * (float) Math.min(1, wait));
        } else if (renderer.hasData() && !renderer.isReady()) {
            drawProgress(graphics, renderStart + (1 - renderStart) * renderer.progress());
        } else {
            renderStart = 0;
        }
    }

    public void continueProgress() {
        renderStart = WAIT_SHARE;
    }

    private void drawProgress(GuiGraphics graphics, float progress) {
        int x = getPositionX() + 2, y = getPositionY() + getSizeHeight() - 4, width = getSizeWidth() - 4;
        graphics.fill(x, y, x + width, y + 2, PROGRESS_TRACK);
        graphics.fill(x, y, x + Math.round(width * progress), y + 2, PROGRESS_COLOR);
    }

    @Override
    protected void onHoverRay(Vector3f from, Vector3f to, boolean pointerOver) {
        var previous = hoverPos;
        hoverPos = null;
        hoverFace = null;
        if (level != null && pointerOver) {
            var hit = ScenePick.pick(level.view(), bounds, from, to);
            if (hit != null) {
                hoverPos = hit.getBlockPos();
                hoverFace = hit.getDirection();
            }
        }
        if (hoverPos == null) {
            hoverItem = ItemStack.EMPTY;
        } else if (!hoverPos.equals(previous) && level != null) {
            var state = level.getBlockState(hoverPos);
            hoverItem = state.getBlock().getCloneItemStack(level, hoverPos, state);
        }
    }

    @Override
    protected List<Component> hoverTooltip() {
        var hovered = markers.hovered();
        if (hovered != null) return hovered.tooltip();
        return tooltip == null ? Collections.emptyList() : tooltip.get();
    }

    @Override
    public Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        var result = super.getXEIIngredientOverMouse(mouseX, mouseY);
        if (result == null && !hoverItem.isEmpty() && isPointerOver(mouseX, mouseY) && !isFlying()) return hoverItem;
        return result;
    }

    @Override
    protected void onSceneClick(double mouseX, double mouseY, int button) {
        var hovered = markers.hovered();
        if (hovered != null && onMarker != null) {
            onMarker.accept(hovered);
            return;
        }
        if (!selectable || button != 0 || hoverPos == null) return;
        selectedPos = hoverPos;
        if (onSelected != null) onSelected.accept(selectedPos, hoverFace == null ? Direction.UP : hoverFace);
    }
}
