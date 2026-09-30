package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.animation.Animation;
import com.gregtechceu.gtceu.uipro.animation.AnimationEngine;
import com.gregtechceu.gtceu.uipro.animation.Eases;
import com.gregtechceu.gtceu.uipro.canvas.CanvasGrid;
import com.gregtechceu.gtceu.uipro.canvas.CanvasLod;
import com.gregtechceu.gtceu.uipro.canvas.CanvasPainter;
import com.gregtechceu.gtceu.uipro.canvas.CanvasRect;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 2D 视图：视口左上角的世界坐标 + 缩放。拖动平移（超过阈值才算拖动），滚轮以鼠标为中心缩放（Shift+滚轮纵向平移），
 * 按钮与适应、定位用缓动过渡（缩放按对数插值、偏移按视口中心插值）。内容由子类提供：图层（画布）或控件子树（流程图）。
 */
public abstract class PlanarView extends Viewport {

    public static final float BUTTON_ZOOM_STEP = 0.1f;
    private static final Animation VIEW_ANIMATION = Animation.of(0.25f, Eases.CUBIC_OUT);
    private static final double WHEEL_PAN = 13;

    protected float minScale = 0.1f, maxScale = 4f;
    private float lodSimplifiedPixelScale = 0.5f, lodBlockPixelScale = 0.2f;
    private boolean allowPan = true, allowZoom = true;
    private boolean clampInside;
    private boolean minScaleFits;
    private float fitPadding = UISizes.SLOT / 2f;
    private float maxFitScale = 1;
    @Nullable
    protected CanvasGrid grid = CanvasGrid.standard();

    protected float offsetX, offsetY, scale = 1;
    private final List<Consumer<PlanarView>> readyActions = new ArrayList<>(1);
    @Nullable
    private Consumer<PlanarView> initialView;
    private boolean viewInitialized;
    private boolean zoomRestored;
    private float defaultScale = 1;
    private float rememberedScale = Float.NaN;
    private int lastViewportWidth = -1, lastViewportHeight = -1;
    private final AnimationEngine animations = new AnimationEngine();
    @Nullable
    private AnimationEngine.Playback viewAnimation;
    @Nullable
    protected CanvasPainter painter;
    private float percentScale = -1;
    private String percentText = "";

    protected PlanarView(String id, int width, int height) {
        super(id, width, height);
    }

    public PlanarView setScaleRange(float minScale, float maxScale) {
        this.minScale = minScale;
        this.maxScale = Math.max(minScale, maxScale);
        this.scale = Mth.clamp(scale, this.minScale, this.maxScale);
        return this;
    }

    public PlanarView setDefaultScale(float defaultScale) {
        this.defaultScale = defaultScale;
        return this;
    }

    public PlanarView setMinScaleFits(boolean minScaleFits) {
        this.minScaleFits = minScaleFits;
        return this;
    }

    public PlanarView setLodThresholds(float simplifiedPixelScale, float blockPixelScale) {
        this.lodSimplifiedPixelScale = simplifiedPixelScale;
        this.lodBlockPixelScale = blockPixelScale;
        return this;
    }

    public PlanarView setAllowPan(boolean allowPan) {
        this.allowPan = allowPan;
        return this;
    }

    public PlanarView setAllowZoom(boolean allowZoom) {
        this.allowZoom = allowZoom;
        return this;
    }

    public boolean isAllowZoom() {
        return allowZoom;
    }

    public PlanarView setClampInside(boolean clampInside) {
        this.clampInside = clampInside;
        return this;
    }

    public PlanarView setFitPadding(float fitPadding) {
        this.fitPadding = fitPadding;
        return this;
    }

    public PlanarView setMaxFitScale(float maxFitScale) {
        this.maxFitScale = maxFitScale;
        return this;
    }

    public PlanarView setGrid(@Nullable CanvasGrid grid) {
        this.grid = grid;
        return this;
    }

    public PlanarView setInitialView(Consumer<PlanarView> initialView) {
        this.initialView = initialView;
        return this;
    }

    public boolean isViewInitialized() {
        return viewInitialized;
    }

    public void whenReady(Consumer<PlanarView> action) {
        if (viewInitialized) action.accept(this);
        else readyActions.add(action);
    }

    @Nullable
    public abstract CanvasRect contentBounds();

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float scale() {
        return scale;
    }

    public float minScale() {
        if (!minScaleFits) return minScale;
        var bounds = contentBounds();
        if (bounds == null || viewportWidth() <= 0 || viewportHeight() <= 0) return minScale;
        float fit = Math.min(viewportWidth() / Math.max(1, bounds.width()), viewportHeight() / Math.max(1, bounds.height()));
        return Math.min(maxScale, Math.max(minScale, Math.min(fit, maxFitScale)));
    }

    @OnlyIn(Dist.CLIENT)
    public float pixelScale() {
        return scale * (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    @OnlyIn(Dist.CLIENT)
    public CanvasLod lod() {
        return CanvasLod.resolve(pixelScale(), lodSimplifiedPixelScale, lodBlockPixelScale);
    }

    protected int obstructedRight() {
        return 0;
    }

    public int unobstructedWidth() {
        int vw = viewportWidth();
        int blocked = obstructedRight();
        return blocked <= 0 ? vw : Math.max(vw / 3, vw - blocked);
    }

    public CanvasRect visibleRect() {
        return new CanvasRect(offsetX, offsetY, viewportWidth() / scale, viewportHeight() / scale);
    }

    public float toWorldX(double screenX) {
        return offsetX + (float) (screenX - viewportX()) / scale;
    }

    public float toWorldY(double screenY) {
        return offsetY + (float) (screenY - viewportY()) / scale;
    }

    public void setView(float offsetX, float offsetY, float scale, boolean animated) {
        float targetScale = Mth.clamp(scale, minScale(), maxScale);
        cancelViewAnimation();
        if (!animated || !isRemote()) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.scale = targetScale;
            clampView();
            onViewChanged();
            return;
        }
        float fromX = this.offsetX, fromY = this.offsetY, fromScale = this.scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scale = targetScale;
        clampView();
        offsetX = this.offsetX;
        offsetY = this.offsetY;
        this.offsetX = fromX;
        this.offsetY = fromY;
        this.scale = fromScale;
        float fromCx = this.offsetX + viewportWidth() / (2 * this.scale), fromCy = this.offsetY + viewportHeight() / (2 * this.scale);
        float toCx = offsetX + viewportWidth() / (2 * targetScale), toCy = offsetY + viewportHeight() / (2 * targetScale);
        float fromLog = (float) Math.log(this.scale), toLog = (float) Math.log(targetScale);
        viewAnimation = animations.play(VIEW_ANIMATION, 0, 1, t -> {
            this.scale = (float) Math.exp(Mth.lerp(t, fromLog, toLog));
            this.offsetX = Mth.lerp(t, fromCx, toCx) - viewportWidth() / (2 * this.scale);
            this.offsetY = Mth.lerp(t, fromCy, toCy) - viewportHeight() / (2 * this.scale);
            onViewChanged();
        });
        viewAnimation.onFinished(() -> {
            clampView();
            onViewChanged();
        });
    }

    protected void onViewChanged() {}

    public void centerOn(float worldX, float worldY, boolean animated) {
        setView(worldX - unobstructedWidth() / (2 * scale), worldY - viewportHeight() / (2 * scale), scale, animated);
    }

    public void focus(CanvasRect rect, boolean animated) {
        float target = Math.min(scale, fitScale(rect.inflate(UISizes.SLOT), 0));
        setView(rect.centerX() - unobstructedWidth() / (2 * target), rect.centerY() - viewportHeight() / (2 * target), target, animated);
    }

    public void reveal(CanvasRect rect, boolean animated) {
        float right = offsetX + unobstructedWidth() / scale, bottom = offsetY + viewportHeight() / scale;
        if (rect.x() >= offsetX && rect.right() <= right && rect.y() >= offsetY && rect.bottom() <= bottom) return;
        focus(rect, animated);
    }

    public void fit(CanvasRect rect, float padding, float maxFitScale, boolean animated) {
        float s = Math.min(maxFitScale, fitScale(rect, padding));
        setView(rect.centerX() - unobstructedWidth() / (2 * s), rect.centerY() - viewportHeight() / (2 * s), s, animated);
    }

    public void fitContent(boolean animated) {
        var bounds = contentBounds();
        if (bounds != null) fit(bounds, fitPadding, maxFitScale, animated);
    }

    public void showStart(float padding, boolean animated) {
        var bounds = contentBounds();
        if (bounds == null) return;
        float s = zoomRestored ? scale : Mth.clamp(defaultScale, minScale(), maxScale);
        float vw = unobstructedWidth() / s, vh = viewportHeight() / s;
        float x = bounds.width() + 2 * padding / s <= vw ? bounds.centerX() - vw / 2 : bounds.x() - padding / s;
        float y = bounds.height() + 2 * padding / s <= vh ? bounds.centerY() - vh / 2 : bounds.y() - padding / s;
        setView(x, y, s, animated);
    }

    public void zoomBy(float factor, boolean animated) {
        float target = Mth.clamp(scale * factor, minScale(), maxScale);
        int width = unobstructedWidth();
        float cx = offsetX + width / (2 * scale), cy = offsetY + viewportHeight() / (2 * scale);
        setView(cx - width / (2 * target), cy - viewportHeight() / (2 * target), target, animated);
    }

    protected void zoomAt(double screenX, double screenY, float target) {
        target = Mth.clamp(target, minScale(), maxScale);
        if (Math.abs(target - scale) < 1e-5f) return;
        float wx = toWorldX(screenX), wy = toWorldY(screenY);
        float localX = (float) (screenX - viewportX()), localY = (float) (screenY - viewportY());
        setView(wx - localX / target, wy - localY / target, target, false);
    }

    protected float fitScale(CanvasRect rect, float padding) {
        float w = Math.max(1, rect.width()), h = Math.max(1, rect.height());
        float s = Math.min((unobstructedWidth() - 2 * padding) / w, (viewportHeight() - 2 * padding) / h);
        return Mth.clamp(s, minScale(), maxScale);
    }

    protected void clampView() {
        var bounds = contentBounds();
        if (bounds == null || viewportWidth() <= 0 || viewportHeight() <= 0) return;
        float viewW = viewportWidth() / scale, viewH = viewportHeight() / scale;
        if (clampInside) {
            offsetX = bounds.width() <= viewW ? bounds.centerX() - viewW / 2 : Mth.clamp(offsetX, bounds.x(), bounds.right() - viewW);
            offsetY = bounds.height() <= viewH ? bounds.centerY() - viewH / 2 : Mth.clamp(offsetY, bounds.y(), bounds.bottom() - viewH);
            return;
        }
        offsetX = Mth.clamp(offsetX, bounds.x() - viewW, bounds.right());
        offsetY = Mth.clamp(offsetY, bounds.y() - viewH, bounds.bottom());
    }

    protected void cancelViewAnimation() {
        if (viewAnimation != null) viewAnimation.cancel();
        viewAnimation = null;
    }

    @Override
    public boolean canZoom() {
        return allowZoom;
    }

    @Override
    public boolean canZoomIn() {
        return allowZoom && scale < maxScale - 1e-4f;
    }

    @Override
    public boolean canZoomOut() {
        return allowZoom && scale > minScale() + 1e-4f;
    }

    @Override
    public void zoomStep(int direction) {
        if (!allowZoom || direction == 0) return;
        float target = steppedScale(direction);
        if (Math.abs(target - scale) > 1e-5f) zoomBy(target / scale, true);
    }

    protected float steppedScale(int direction) {
        float target = Math.round(scale / BUTTON_ZOOM_STEP + Integer.signum(direction)) * BUTTON_ZOOM_STEP;
        return Mth.clamp(target, minScale(), maxScale);
    }

    @Override
    public String percentText() {
        if (percentScale != scale) {
            percentScale = scale;
            percentText = Math.round(scale * 100) + "%";
        }
        return percentText;
    }

    @Override
    public void fitView() {
        fitContent(true);
    }

    @Override
    protected void restoreZoom(float zoom) {
        if (!allowZoom) return;
        scale = Mth.clamp(zoom, minScale, maxScale);
        zoomRestored = true;
    }

    @Override
    protected float rememberedZoom() {
        return allowZoom && viewInitialized ? scale : Float.NaN;
    }

    @OnlyIn(Dist.CLIENT)
    protected void prepareView() {
        int vw = viewportWidth(), vh = viewportHeight();
        if (!viewInitialized) {
            lastViewportWidth = vw;
            lastViewportHeight = vh;
            if (initialView != null) initialView.accept(this);
            else showStart(UISizes.SLOT / 2f, false);
            for (var action : readyActions) action.accept(this);
            readyActions.clear();
            viewInitialized = true;
        }
        if (lastViewportWidth > 0 && lastViewportHeight > 0 && (vw != lastViewportWidth || vh != lastViewportHeight)) {
            if (keepCenterOnResize()) {
                offsetX += (lastViewportWidth - vw) / (2 * scale);
                offsetY += (lastViewportHeight - vh) / (2 * scale);
            }
            if (scale < minScale()) scale = minScale();
            clampView();
            onViewChanged();
        }
        lastViewportWidth = vw;
        lastViewportHeight = vh;
        animations.updateFrame();
        if (isRememberView() && allowZoom && scale != rememberedScale) {
            rememberedScale = scale;
            ViewPrefs.remember(zoomKey(), scale);
        }
    }

    protected boolean keepCenterOnResize() {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    protected void drawGrid(GuiGraphics graphics) {
        if (grid == null) return;
        if (painter == null) painter = new CanvasPainter();
        int vx = viewportX(), vy = viewportY(), vw = viewportWidth(), vh = viewportHeight();
        painter.beginScreen(graphics, CanvasRect.of(vx, vy, vw, vh));
        grid.draw(painter, scale, offsetX, offsetY, vx, vy, vw, vh);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected boolean onViewPress(double mouseX, double mouseY, int button) {
        cancelViewAnimation();
        gesture.press(button, mouseX, mouseY, offsetX, offsetY);
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void onViewDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int pressed = gesture.button();
        if (!allowPan || pressed != 0 && pressed != 2) return;
        if (gesture.drag(mouseX, mouseY)) {
            offsetX = gesture.panX(mouseX, scale);
            offsetY = gesture.panY(mouseY, scale);
            clampView();
            onViewChanged();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void onViewRelease(double mouseX, double mouseY, int button, boolean click) {
        if (click && isInViewport(mouseX, mouseY)) onViewClick(mouseX, mouseY, button);
    }

    @OnlyIn(Dist.CLIENT)
    protected void onViewClick(double mouseX, double mouseY, int button) {}

    @Override
    @OnlyIn(Dist.CLIENT)
    protected boolean onViewWheel(double mouseX, double mouseY, double wheelDelta) {
        if (Screen.hasShiftDown() || !allowZoom) {
            if (!allowPan) return false;
            float before = offsetY;
            offsetY -= (float) (Math.signum(wheelDelta) * WHEEL_PAN / scale);
            clampView();
            if (before == offsetY) return false;
            cancelViewAnimation();
            onViewChanged();
            return true;
        }
        cancelViewAnimation();
        zoomAt(mouseX, mouseY, steppedScale(wheelDelta > 0 ? 1 : -1));
        return true;
    }
}
