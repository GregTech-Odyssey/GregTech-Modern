package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.ViewTransform;
import com.gregtechceu.gtceu.uipro.canvas.CanvasRect;
import com.gregtechceu.gtceu.uipro.render.UIClip;
import com.gregtechceu.gtceu.uipro.render.UIPixels;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

/**
 * 内容是真实控件子树的 2D 视图（流程图等）：内容挂在一个带 {@link ViewTransform} 的内容根下，平移缩放只改矩阵、不动布局，
 * 控件的命中、悬停、EMI 都经逆变换送达。视口随"内容 × 缩放"伸缩，不超过所在窗口允许的页面尺寸。
 */
public class FlowView extends PlanarView {

    public static final float MIN_ZOOM = 0.25f;
    public static final float MAX_ZOOM = 2f;
    public static final String SHARED_ZOOM_KEY = "flow";
    private static final int MIN_SIZE = 2 * UISizes.SLOT_SIZE;
    private static final int EDGE = 4;
    private static final int EDGE_ALPHA = 0x30;

    private final UIElement content;
    private final UIElement root = new UIElement();
    private final ViewTransform transform = new ViewTransform();
    private boolean windowLimited;
    private boolean windowInventory;
    private int maxWidth = Integer.MAX_VALUE, maxHeight = Integer.MAX_VALUE;
    private int minWidth = MIN_SIZE, minHeight = MIN_SIZE;
    private int appliedWidth = -1, appliedHeight = -1;
    private boolean sizing;

    public FlowView(String id, UIElement content, boolean zoomable) {
        super(id, Math.max(MIN_SIZE, content.getSizeWidth()), Math.max(MIN_SIZE, content.getSizeHeight()));
        this.content = content;
        setFrame(true, 1);
        setResizable(false);
        setRememberView(zoomable);
        setZoomKey(SHARED_ZOOM_KEY);
        setAllowZoom(zoomable);
        setClampInside(true);
        setFitPadding(0);
        setScaleRange(MIN_ZOOM, MAX_ZOOM);
        root.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(1).top(1));
        root.setTransform(transform);
        root.addChild(content);
        addChild(root);
        setInitialView(view -> view.setView(0, 0, scale, false));
        fitToContent();
    }

    public UIElement getContent() {
        return content;
    }

    public FlowView limitToWindow(boolean inventory) {
        this.windowLimited = true;
        this.windowInventory = inventory;
        return this;
    }

    public FlowView maxSize(int width, int height) {
        this.maxWidth = Math.max(MIN_SIZE, width);
        this.maxHeight = Math.max(MIN_SIZE, height);
        fitToContent();
        return this;
    }

    public FlowView minSize(int width, int height) {
        this.minWidth = Math.max(MIN_SIZE, width);
        this.minHeight = Math.max(MIN_SIZE, height);
        fitToContent();
        return this;
    }

    @Override
    @Nullable
    public CanvasRect contentBounds() {
        return CanvasRect.of(0, 0, Math.max(1, content.getSizeWidth()), Math.max(1, content.getSizeHeight()));
    }

    private void fitToContent() {
        if (content == null || sizing) return;
        int w = Math.max(Math.min(minWidth, maxWidth), Math.min(Math.round(content.getSizeWidth() * scale) + 2, maxWidth));
        int h = Math.max(Math.min(minHeight, maxHeight), Math.min(Math.round(content.getSizeHeight() * scale) + 2, maxHeight));
        if (w == appliedWidth && h == appliedHeight) return;
        appliedWidth = w;
        appliedHeight = h;
        sizing = true;
        try {
            setPreferredSize(w, h);
        } finally {
            sizing = false;
        }
    }

    @Override
    protected void onChildSizeUpdate(Widget child) {
        super.onChildSizeUpdate(child);
        if (child == root) {
            fitToContent();
            clampView();
            updateTransform();
        }
    }

    @Override
    protected boolean keepCenterOnResize() {
        return false;
    }

    @Override
    protected void onViewChanged() {
        fitToContent();
        updateTransform();
    }

    private void updateTransform() {
        if (isRemote()) clientUpdateTransform();
        else transform.set(-offsetX * scale, -offsetY * scale, scale);
    }

    @OnlyIn(Dist.CLIENT)
    private void clientUpdateTransform() {
        transform.set(UIPixels.snap(-offsetX * scale), UIPixels.snap(-offsetY * scale), scale);
    }

    @Override
    public void fitView() {
        if (!isAllowZoom()) return;
        int w = content.getSizeWidth(), h = content.getSizeHeight();
        float target = Math.min(1, Math.min((maxWidth - 2) / (float) Math.max(1, w), (maxHeight - 2) / (float) Math.max(1, h)));
        setView(0, 0, Mth.clamp(target, MIN_ZOOM, MAX_ZOOM), false);
    }

    @Override
    public void zoomStep(int direction) {
        if (!isAllowZoom() || direction == 0) return;
        float target = Mth.clamp(steppedScale(direction), MIN_ZOOM, MAX_ZOOM);
        setView(offsetX, offsetY, target, false);
    }

    @Override
    public void initWidget() {
        super.initWidget();
        if (isRemote()) updateLimits();
        fitToContent();
        updateTransform();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void onScreenSizeUpdate(int screenWidth, int screenHeight) {
        super.onScreenSizeUpdate(screenWidth, screenHeight);
        updateLimits();
        fitToContent();
    }

    @OnlyIn(Dist.CLIENT)
    private void updateLimits() {
        if (!windowLimited) return;
        var window = MachineWindow.of(this);
        maxWidth = Math.max(MIN_SIZE, window != null ? window.clientPageWidthLimit() : Integer.MAX_VALUE);
        maxHeight = Math.max(MIN_SIZE, window != null ? window.clientPageHeightLimitFor(windowInventory) :
                MachineWindow.clientPageHeightLimit(windowInventory));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawFrame(graphics, mouseX, mouseY);
        if (viewportWidth() <= 0 || viewportHeight() <= 0) return;
        prepareView();
        updateTransform();
        pushClip(graphics);
        drawGrid(graphics);
        if (painter != null) painter.flush();
        UIClip.pop(graphics);
        drawChildren(graphics, mouseX, mouseY, partialTicks);
        pushClip(graphics);
        drawEdges(graphics);
        UIClip.pop(graphics);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawEdges(GuiGraphics graphics) {
        int x = viewportX(), y = viewportY(), w = viewportWidth(), h = viewportHeight();
        float viewW = w / scale, viewH = h / scale;
        boolean left = offsetX > 0.5f, top = offsetY > 0.5f;
        boolean right = offsetX + viewW < content.getSizeWidth() - 0.5f, bottom = offsetY + viewH < content.getSizeHeight() - 0.5f;
        for (int i = 0; i < EDGE; i++) {
            int color = (EDGE_ALPHA * (EDGE - i) / EDGE) << 24;
            if (left) graphics.fill(x + i, y, x + i + 1, y + h, color);
            if (right) graphics.fill(x + w - i - 1, y, x + w - i, y + h, color);
            if (top) graphics.fill(x, y + i, x + w, y + i + 1, color);
            if (bottom) graphics.fill(x, y + h - i - 1, x + w, y + h - i, color);
        }
    }
}
