package com.gregtechceu.gtceu.uiwidgets.structure;

import com.lowdragmc.lowdraglib.client.utils.RenderUtils;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

@OnlyIn(Dist.CLIENT)
public final class StructureScene extends WidgetGroup {

    public static final int ALL_LAYERS = PreviewLevel.ALL_LAYERS;
    private static final float FIT_PER_SIZE = 5f;
    private static final float DEFAULT_PER_SIZE = 3.5f;
    private static final float FOV = (float) Math.toRadians(60);
    private static final float MIN_NEAR = 0.05f;
    private static final float NEAR_PER_ZOOM = 0.005f;
    private static final float FAR_PLANE = 10000f;
    private static final float SELECTION_RED = 0.6f;
    private static final float SELECTION_SCALE = 1.01f;
    private static final float MAX_FRAME_SECONDS = 0.1f;
    private static final int IDLE_TICKS = 40;
    private static final int PROGRESS_COLOR = 0xFF55C255;
    private static final int PROGRESS_TRACK = 0x40000000;
    private static final float WAIT_SHARE = 0.7f;
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final Set<StructureScene> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static int ticks;

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, StructureScene::onClientTick);
    }

    private final boolean movable;
    private final StructureRenderer renderer = new StructureRenderer();
    private final SceneCamera camera = new SceneCamera();
    private final SceneMarkers markers = new SceneMarkers();
    private final PreviewBounds bounds = new PreviewBounds();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f combined = new Matrix4f();
    @Nullable
    private PreviewLevel level;
    @Setter
    private BiPredicate<Double, Double> blocked = (x, y) -> false;
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
    private int pendingLayer = ALL_LAYERS;
    private float pendingZoom = -1;
    private boolean hasPending;
    private boolean hasMatrix;
    private boolean dragging;
    @Nullable
    private BlockPos hoverPos;
    @Nullable
    private Vec3 hoverHit;
    @Nullable
    private Direction hoverFace;
    @Nullable
    private BlockPos clickPos;
    @Nullable
    private BlockPos selectedPos;
    private ItemStack hoverItem = ItemStack.EMPTY;
    private long lastFrame;
    private int drawnTick;
    private boolean registered;
    private boolean closeHooked;
    private final List<Overlay> overlays = new ArrayList<>();

    public record Marker(Vector3f pos, int color, boolean selected, List<Component> tooltip) {}

    public record Camera(Vector3f center, float yaw, float pitch, float zoom) {}

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
        super(0, 0, width, height);
        this.movable = movable;
        setClientSideWidget();
    }

    public static float fitZoom(int width, int height, int depth) {
        return fitZoom(Math.max(width, Math.max(height, depth)), FIT_PER_SIZE);
    }

    private static float fitZoom(int size, float perSize) {
        return perSize * (float) Math.sqrt(Math.max(size, 1));
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (LIVE.isEmpty()) return;
        for (var scene : LIVE.toArray(new StructureScene[0])) {
            if (scene != null && ticks - scene.drawnTick > IDLE_TICKS) scene.idle();
        }
    }

    private void idle() {
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

    public void setMarkers(List<Marker> markers) {
        this.markers.set(markers);
    }

    public void show(@Nullable Long2ObjectOpenHashMap<BlockState> blocks, int onlyY, float zoom) {
        if (blocks != null) pendingBlocks = blocks;
        pendingLayer = onlyY;
        if (zoom > 0) pendingZoom = zoom;
        hasPending = true;
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
        hoverPos = clickPos = selectedPos = null;
        hoverItem = ItemStack.EMPTY;
    }

    @Override
    public void setGui(ModularUI gui) {
        super.setGui(gui);
        if (gui != null && !closeHooked) {
            closeHooked = true;
            gui.registerCloseListener(this::idle);
        }
    }

    private void applyPending() {
        if (!hasPending) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        hasPending = false;
        boolean changed = pendingBlocks != null;
        if (changed) {
            if (level == null) level = new PreviewLevel(minecraft.level);
            level.setBlocks(pendingBlocks);
            pendingBlocks = null;
            hoverPos = clickPos = selectedPos = null;
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

    public Camera camera() {
        return camera.snapshot();
    }

    public void recenter(@Nullable Camera camera) {
        this.camera.recenter(camera);
    }

    public Vector3f getCenter() {
        return camera.center();
    }

    public void zoomStep(int direction) {
        camera.zoomStep(direction);
    }

    public String percentText() {
        return camera.percentText();
    }

    public void resetZoom() {
        camera.resetZoom();
    }

    public void resetView() {
        camera.resetView();
    }

    public void focus(float x, float z) {
        camera.focus(x, z);
    }

    @Override
    public boolean isMouseOverElement(double mouseX, double mouseY) {
        return super.isMouseOverElement(mouseX, mouseY) && !blocked.test(mouseX, mouseY);
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawnTick = ticks;
        if (!registered) {
            registered = true;
            LIVE.add(this);
        }
        if (!hasPending && !renderer.hasData() && reloader != null) reloader.run();
        applyPending();
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(MAX_FRAME_SECONDS, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (movable && seconds > 0) move(seconds);
        if (renderer.hasData()) {
            renderScene(graphics, mouseX, mouseY, partialTicks);
            if (hasMatrix) {
                markers.draw(graphics, combined, camera.eye(), level == null ? null : level.view(), bounds, getPositionX(), getPositionY(),
                        getSizeWidth(), getSizeHeight(), isMouseOverElement(mouseX, mouseY), mouseX, mouseY);
            }
        }
        double wait = waiting.getAsDouble();
        if (wait >= 0) {
            drawProgress(graphics, WAIT_SHARE * (float) Math.min(1, wait));
        } else if (renderer.hasData() && !renderer.isReady()) {
            drawProgress(graphics, renderStart + (1 - renderStart) * renderer.progress());
        } else {
            renderStart = 0;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    public void continueProgress() {
        renderStart = WAIT_SHARE;
    }

    private void drawProgress(GuiGraphics graphics, float progress) {
        int x = getPositionX() + 2, y = getPositionY() + getSizeHeight() - 4, width = getSizeWidth() - 4;
        graphics.fill(x, y, x + width, y + 2, PROGRESS_TRACK);
        graphics.fill(x, y, x + Math.round(width * progress), y + 2, PROGRESS_COLOR);
    }

    private void renderScene(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() instanceof LoadingOverlay) return;
        renderer.upload();
        for (var overlay : overlays) overlay.renderer.upload();
        var window = minecraft.getWindow();
        var pose = graphics.pose().last().pose();
        var topLeft = pose.transform(new Vector4f(getPositionX(), getPositionY(), 0, 1));
        var bottomRight = pose.transform(new Vector4f(getPositionX() + getSizeWidth(), getPositionY() + getSizeHeight(), 0, 1));
        float gx = topLeft.x(), gy = topLeft.y(), gw = bottomRight.x() - gx, gh = bottomRight.y() - gy;
        if (gw <= 0 || gh <= 0) return;
        double sx = window.getWidth() / (double) window.getGuiScaledWidth(), sy = window.getHeight() / (double) window.getGuiScaledHeight();
        int vw = (int) (gw * sx), vh = (int) (gh * sy), vx = (int) (gx * sx), vy = window.getHeight() - (int) (gy * sy) - vh;
        if (vw <= 0 || vh <= 0) return;
        var eye = camera.eye();
        float near = Math.max(MIN_NEAR, camera.zoom() * NEAR_PER_ZOOM);
        projection.setPerspective(FOV, vw / (float) vh, near, FAR_PLANE);
        viewMatrix.setLookAt(eye, camera.center(), UP);
        combined.set(projection).mul(viewMatrix);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.viewport(vx, vy, vw, vh);
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(projection), VertexSorting.byDistance(eye));
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(viewMatrix);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.enableCull();
        try {
            var frustum = new FrustumIntersection(combined);
            renderer.render(frustum, eye, partialTicks);
            for (var overlay : overlays) overlay.renderer.render(frustum, eye, partialTicks);
            hasMatrix = true;
            if (selectedPos != null) RenderUtils.renderBlockOverLay(new PoseStack(), selectedPos, SELECTION_RED, 0, 0, SELECTION_SCALE);
        } finally {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
            RenderSystem.restoreProjectionMatrix();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
        }
        var mouse = pose.transform(new Vector4f(mouseX, mouseY, 0, 1));
        updateHover(mouseX, mouseY, (mouse.x() - gx) / gw * 2 - 1, 1 - (mouse.y() - gy) / gh * 2);
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        var hovered = markers.hovered();
        if (hovered != null && !hovered.tooltip().isEmpty()) {
            graphics.renderComponentTooltip(Minecraft.getInstance().font, hovered.tooltip(), mouseX, mouseY);
        }
    }

    private void updateHover(int mouseX, int mouseY, float ndcX, float ndcY) {
        var previous = hoverPos;
        hoverPos = null;
        hoverFace = null;
        hoverHit = null;
        if (level != null && isMouseOverElement(mouseX, mouseY)) {
            var inverse = new Matrix4f(combined).invert();
            var from = inverse.transformProject(ndcX, ndcY, -1, new Vector3f());
            var to = inverse.transformProject(ndcX, ndcY, 1, new Vector3f());
            var hit = ScenePick.pick(level.view(), bounds, from, to);
            if (hit != null) {
                hoverPos = hit.getBlockPos();
                hoverFace = hit.getDirection();
                hoverHit = hit.getLocation();
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
    public Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        var result = super.getXEIIngredientOverMouse(mouseX, mouseY);
        if (result == null && !hoverItem.isEmpty() && isMouseOverElement(mouseX, mouseY)) return hoverItem;
        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        var hovered = markers.hovered();
        if (hovered != null && onMarker != null && isMouseOverElement(mouseX, mouseY) && (button == 0 || button == 1)) {
            onMarker.accept(hovered);
            return true;
        }
        if (isMouseOverElement(mouseX, mouseY)) {
            dragging = true;
            clickPos = hoverPos;
            if (button == 0) camera.pivotAt(hoverHit);
            return true;
        }
        dragging = false;
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        if (hoverPos != null && hoverPos.equals(clickPos)) {
            selectedPos = hoverPos;
            clickPos = null;
            if (onSelected != null) onSelected.accept(selectedPos, hoverFace == null ? Direction.UP : hoverFace);
            return true;
        }
        clickPos = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (super.mouseWheelMove(mouseX, mouseY, wheelDelta)) return true;
        if (!isMouseOverElement(mouseX, mouseY)) return false;
        camera.wheel(wheelDelta);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 1) {
            camera.pan(dragX, dragY);
            return true;
        }
        if (dragging) {
            camera.rotate(dragX, dragY);
            return false;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void move(float seconds) {
        if (gui != null && gui.getModularUIGui() != null && gui.getModularUIGui().lastFocus instanceof TextFieldWidget) return;
        camera.move(Minecraft.getInstance().getWindow().getWindow(), seconds);
    }
}
