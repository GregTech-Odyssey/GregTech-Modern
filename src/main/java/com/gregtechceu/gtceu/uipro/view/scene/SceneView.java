package com.gregtechceu.gtceu.uipro.view.scene;

import com.gregtechceu.gtceu.uipro.view.Viewport;
import com.gregtechceu.gtceu.uipro.view.ZoomBar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * 3D 视图：透视相机（{@link SceneCamera}，以相机为中心转向、WASD/空格/Shift 飞行、滚轮调速）、GL 视口换算与状态恢复、
 * 点击与拖动的阈值区分、每帧一次的拾取射线，以及长时间未绘制或关界面时释放显存。内容由子类渲染与拾取。
 */
@OnlyIn(Dist.CLIENT)
public abstract class SceneView extends Viewport {

    private static final float FOV = (float) Math.toRadians(60);
    private static final float MIN_NEAR = 0.05f;
    private static final float NEAR_PER_ZOOM = 0.005f;
    private static final float FAR_PLANE = 10000f;
    private static final float MAX_FRAME_SECONDS = 0.1f;
    private static final int IDLE_TICKS = 40;
    private static final int SPEED_HINT_TICKS = 30;
    private static final int SPEED_HINT_COLOR = 0xFFFFFFFF;
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final Set<SceneView> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static int ticks;

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, SceneView::onClientTick);
    }

    public record Camera(Vector3f center, float yaw, float pitch, float zoom) {}

    protected final SceneCamera camera = new SceneCamera();
    protected final boolean movable;
    protected final Matrix4f projection = new Matrix4f();
    protected final Matrix4f viewMatrix = new Matrix4f();
    protected final Matrix4f combined = new Matrix4f();
    protected boolean hasMatrix;
    private boolean zoomButtons;
    @Nullable
    private BooleanSupplier minimapAvailable;
    private long lastFrame;
    private int drawnTick;
    private int speedShownTick = -1;
    private boolean registered;

    protected SceneView(String id, int width, int height, boolean movable) {
        super(id, width, height);
        this.movable = movable;
        setFrame(false, 0);
        setResizable(false);
        setClientSideWidget();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (LIVE.isEmpty()) return;
        for (var scene : LIVE.toArray(new SceneView[0])) {
            if (scene != null && ticks - scene.drawnTick > IDLE_TICKS) scene.idle();
        }
    }

    protected abstract void idle();

    protected abstract boolean hasContent();

    protected abstract void beforeRender();

    protected abstract void renderContent(FrustumIntersection frustum, Vector3f eye, float partialTicks);

    protected void renderExtras(float partialTicks) {}

    protected abstract void onHoverRay(Vector3f from, Vector3f to, boolean pointerOver);

    protected abstract void onSceneClick(double mouseX, double mouseY, int button);

    protected List<Component> hoverTooltip() {
        return Collections.emptyList();
    }

    @Override
    protected void onUIClosed() {
        super.onUIClosed();
        idle();
    }

    public SceneView setZoomButtons(boolean zoomButtons) {
        this.zoomButtons = zoomButtons;
        return this;
    }

    public SceneView setMinimapAvailable(@Nullable BooleanSupplier available) {
        this.minimapAvailable = available;
        return this;
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

    public void resetView() {
        camera.resetView();
    }

    public void resetZoom() {
        camera.resetZoom();
    }

    public void focus(float x, float z) {
        camera.focus(x, z);
    }

    protected float reach() {
        return camera.zoom();
    }

    @Override
    public boolean canZoom() {
        return zoomButtons;
    }

    @Override
    public boolean canZoomIn() {
        return zoomButtons;
    }

    @Override
    public boolean canZoomOut() {
        return zoomButtons;
    }

    @Override
    public void zoomStep(int direction) {
        if (zoomButtons) camera.zoomStep(direction, reach());
    }

    @Override
    public String percentText() {
        return camera.percentText();
    }

    @Override
    public void fitView() {
        resetView();
    }

    @Override
    public void percentClicked() {
        resetZoom();
    }

    @Override
    public String percentTooltip() {
        return ZoomBar.DISTANCE;
    }

    @Override
    public boolean hasMinimap() {
        return minimapAvailable != null;
    }

    @Override
    public boolean isMinimapAvailable() {
        return minimapAvailable != null && minimapAvailable.getAsBoolean();
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawnTick = ticks;
        if (!registered) {
            registered = true;
            LIVE.add(this);
        }
        beforeRender();
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(MAX_FRAME_SECONDS, (now - lastFrame) / 1e9f);
        lastFrame = now;
        boolean over = isPointerOver(mouseX, mouseY);
        if (movable && seconds > 0 && over && !isTextInputFocused()) camera.move(Minecraft.getInstance().getWindow().getWindow(), seconds);
        if (hasContent()) renderScene(graphics, mouseX, mouseY, partialTicks, over);
        drawOverScene(graphics, mouseX, mouseY, partialTicks);
        if (movable && speedShownTick >= 0 && ticks - speedShownTick < SPEED_HINT_TICKS) {
            graphics.drawString(Minecraft.getInstance().font, String.format("×%.2f", SceneCamera.speedScale()), getPositionX() + 4,
                    getPositionY() + 4, SPEED_HINT_COLOR, true);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        drawChildren(graphics, mouseX, mouseY, partialTicks);
    }

    protected void drawOverScene(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {}

    private void renderScene(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, boolean over) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() instanceof LoadingOverlay) return;
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
            renderContent(new FrustumIntersection(combined), eye, partialTicks);
            hasMatrix = true;
            renderExtras(partialTicks);
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
        float ndcX = (mouse.x() - gx) / gw * 2 - 1, ndcY = 1 - (mouse.y() - gy) / gh * 2;
        var inverse = new Matrix4f(combined).invert();
        var from = inverse.transformProject(ndcX, ndcY, -1, new Vector3f());
        var to = inverse.transformProject(ndcX, ndcY, 1, new Vector3f());
        onHoverRay(from, to, over && !isInteracting());
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (isInteracting() || !isPointerOver(mouseX, mouseY)) return;
        var lines = hoverTooltip();
        if (lines.isEmpty()) return;
        if (gui != null && gui.getModularUIGui() != null) gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
        else graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    protected boolean isFlying() {
        return movable && SceneCamera.moving(Minecraft.getInstance().getWindow().getWindow());
    }

    @Override
    protected boolean onViewPress(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return false;
        gesture.press(button, mouseX, mouseY, 0, 0);
        return true;
    }

    @Override
    protected void onViewDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!gesture.drag(mouseX, mouseY)) return;
        double dx = gesture.stepX(mouseX), dy = gesture.stepY(mouseY);
        if (gesture.button() == 1) camera.pan(dx, dy, reach());
        else camera.rotate(dx, dy);
    }

    @Override
    protected void onViewRelease(double mouseX, double mouseY, int button, boolean click) {
        if (click && isInViewport(mouseX, mouseY)) onSceneClick(mouseX, mouseY, button);
    }

    @Override
    protected boolean onViewWheel(double mouseX, double mouseY, double wheelDelta) {
        if (!movable) return false;
        SceneCamera.adjustSpeed(wheelDelta);
        speedShownTick = ticks;
        return true;
    }
}
