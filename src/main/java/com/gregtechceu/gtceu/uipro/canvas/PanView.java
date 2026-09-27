package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.UIPreferences;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.UUID;

public class PanView extends WidgetGroup implements ILayoutItem {

    public static final float MIN_ZOOM = 0.25f;
    public static final float MAX_ZOOM = 2f;
    public static final float ZOOM_STEP = 0.1f;
    private static final int OUTSIDE = -100000;
    private static final int MIN_SIZE = 2 * UISizes.SLOT;
    private static final int EDGE = 4;
    private static final int EDGE_ALPHA = 0x30;
    private static final double WHEEL_STEP = 13;

    private final UIElement content;
    private final LayoutStyle layoutStyle;
    private final PanGesture pan = new PanGesture();
    private final Vector4f scissorMin = new Vector4f(), scissorMax = new Vector4f();
    private boolean windowLimited;
    private boolean windowInventory;
    private int maxWidth = Integer.MAX_VALUE, maxHeight = Integer.MAX_VALUE;
    private float offsetX, offsetY;
    private float scale = 1;
    @Nullable
    private String zoomKey;
    private boolean zoomLocked;
    private float percentScale = -1;
    private String percentText = "";
    private int styleWidth, styleHeight;
    private boolean applying;
    @Nullable
    private CanvasPainter painter;
    @Nullable
    private CanvasGrid grid;

    public PanView(UIElement content) {
        super(0, 0, Math.max(MIN_SIZE, content.getSizeWidth()), Math.max(MIN_SIZE, content.getSizeHeight()));
        this.content = content;
        this.styleWidth = getSizeWidth();
        this.styleHeight = getSizeHeight();
        this.layoutStyle = LayoutStyle.fixed(styleWidth, styleHeight, () -> UIElement.markLayoutDirty(this));
        addWidget(content);
    }

    public PanView limitToWindow(boolean inventory) {
        this.windowLimited = true;
        this.windowInventory = inventory;
        return this;
    }

    public PanView zoomable(String zoomKey) {
        this.zoomKey = zoomKey;
        return this;
    }

    public boolean isZoomable() {
        return zoomKey != null;
    }

    public UIElement getContent() {
        return content;
    }

    public boolean isPanning() {
        return pan.isPanning();
    }

    public float getScale() {
        return scale;
    }

    public String percentText() {
        if (percentScale != scale) {
            percentScale = scale;
            percentText = Math.round(scale * 100) + "%";
        }
        return percentText;
    }

    public boolean isZoomLocked() {
        return zoomLocked;
    }

    public boolean canZoomIn() {
        return isZoomable() && !zoomLocked && scale < MAX_ZOOM - 1e-4f;
    }

    public boolean canZoomOut() {
        return isZoomable() && !zoomLocked && scale > MIN_ZOOM + 1e-4f;
    }

    @Override
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        if (isRemote()) {
            restoreZoom();
            updateLimits();
        }
        applySize();
    }

    @OnlyIn(Dist.CLIENT)
    private void restoreZoom() {
        var player = player();
        if (zoomKey == null || player == null) return;
        float saved = UIPreferences.getZoom(player, zoomKey);
        if (Float.isNaN(saved)) return;
        zoomLocked = true;
        scale = Mth.clamp(saved, MIN_ZOOM, MAX_ZOOM);
    }

    @Nullable
    private UUID player() {
        return gui != null && gui.entityPlayer != null && gui.entityPlayer.level().isClientSide ? gui.entityPlayer.getUUID() : null;
    }

    @OnlyIn(Dist.CLIENT)
    public void toggleZoomLock() {
        var player = player();
        if (zoomKey == null || player == null) return;
        zoomLocked = !zoomLocked;
        if (zoomLocked) UIPreferences.putZoom(player, zoomKey, scale);
        else UIPreferences.removeZoom(player, zoomKey);
    }

    @OnlyIn(Dist.CLIENT)
    public void zoomBy(float factor) {
        zoomAround(getPositionX() + getSizeWidth() / 2.0, getPositionY() + getSizeHeight() / 2.0, scale * factor);
    }

    @OnlyIn(Dist.CLIENT)
    public void zoomStep(int direction) {
        zoomAround(getPositionX() + getSizeWidth() / 2.0, getPositionY() + getSizeHeight() / 2.0, stepped(direction));
    }

    private float stepped(int direction) {
        return Math.round(scale / ZOOM_STEP + direction) * ZOOM_STEP;
    }

    @OnlyIn(Dist.CLIENT)
    public void resetZoom() {
        zoomAround(getPositionX() + getSizeWidth() / 2.0, getPositionY() + getSizeHeight() / 2.0, 1);
    }

    @OnlyIn(Dist.CLIENT)
    private void zoomAround(double screenX, double screenY, float target) {
        if (!isZoomable() || zoomLocked) return;
        target = Mth.clamp(target, MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(target - scale) < 1e-5f) return;
        double localX = screenX - getPositionX(), localY = screenY - getPositionY();
        float contentX = (float) (localX / scale) - content.getSelfPositionX();
        float contentY = (float) (localY / scale) - content.getSelfPositionY();
        scale = target;
        offsetX = contentX - (float) (localX / scale);
        offsetY = contentY - (float) (localY / scale);
        applySize();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void onScreenSizeUpdate(int screenWidth, int screenHeight) {
        super.onScreenSizeUpdate(screenWidth, screenHeight);
        updateLimits();
        applySize();
    }

    @OnlyIn(Dist.CLIENT)
    private void updateLimits() {
        if (!windowLimited) return;
        var window = MachineWindow.of(this);
        maxWidth = Math.max(MIN_SIZE, window != null ? window.clientPageWidthLimit() : Integer.MAX_VALUE);
        maxHeight = Math.max(MIN_SIZE, window != null ? window.clientPageHeightLimitFor(windowInventory) : MachineWindow.clientPageHeightLimit(windowInventory));
    }

    private void applySize() {
        int w = Math.max(MIN_SIZE, Math.min(Math.round(content.getSizeWidth() * scale), maxWidth));
        int h = Math.max(MIN_SIZE, Math.min(Math.round(content.getSizeHeight() * scale), maxHeight));
        if (w != styleWidth || h != styleHeight) {
            styleWidth = w;
            styleHeight = h;
            layoutStyle.size(w, h);
        }
        placeContent();
    }

    @Override
    protected void onChildSizeUpdate(Widget child) {
        super.onChildSizeUpdate(child);
        if (child == content && !applying) {
            applying = true;
            try {
                applySize();
            } finally {
                applying = false;
            }
        }
    }

    @Override
    public void setSize(Size size) {
        super.setSize(size);
        placeContent();
    }

    private float viewWidth() {
        return getSizeWidth() / scale;
    }

    private float viewHeight() {
        return getSizeHeight() / scale;
    }

    private boolean overflowX() {
        return content.getSizeWidth() > viewWidth() + 0.5f;
    }

    private boolean overflowY() {
        return content.getSizeHeight() > viewHeight() + 0.5f;
    }

    private void placeContent() {
        if (content == null) return;
        float vw = viewWidth(), vh = viewHeight();
        int cw = content.getSizeWidth(), ch = content.getSizeHeight();
        offsetX = Mth.clamp(offsetX, 0, Math.max(0, cw - vw));
        offsetY = Mth.clamp(offsetY, 0, Math.max(0, ch - vh));
        int x = overflowX() ? -Math.round(offsetX) : Math.round((vw - cw) / 2);
        int y = overflowY() ? -Math.round(offsetY) : Math.round((vh - ch) / 2);
        if (content.getSelfPositionX() != x || content.getSelfPositionY() != y) content.setSelfPosition(new Position(x, y));
    }

    private boolean isInViewport(double mouseX, double mouseY) {
        return isMouseOver(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY);
    }

    private double toContentX(double mouseX) {
        return getPositionX() + (mouseX - getPositionX()) / scale;
    }

    private double toContentY(double mouseY) {
        return getPositionY() + (mouseY - getPositionY()) / scale;
    }

    @Override
    public boolean isMouseOverElement(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY);
    }

    @Override
    public @Nullable Widget getHoverElement(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY) && !pan.isPanning() ? super.getHoverElement(toContentX(mouseX), toContentY(mouseY)) : null;
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY) && !pan.isPanning() ? super.getXEIIngredientOverMouse(toContentX(mouseX), toContentY(mouseY)) : null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseMoved(double mouseX, double mouseY) {
        return super.mouseMoved(toContentX(mouseX), toContentY(mouseY));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isActive() || !isInViewport(mouseX, mouseY)) return false;
        if (super.mouseClicked(toContentX(mouseX), toContentY(mouseY), button)) return true;
        if ((button == 0 || button == 2) && (overflowX() || overflowY())) {
            pan.press(button, mouseX, mouseY, offsetX, offsetY);
            return true;
        }
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pan.isPressed()) {
            if (pan.drag(mouseX, mouseY, true)) {
                offsetX = pan.offsetX(mouseX, scale);
                offsetY = pan.offsetY(mouseY, scale);
                placeContent();
            }
            return true;
        }
        return super.mouseDragged(toContentX(mouseX), toContentY(mouseY), button, dragX / scale, dragY / scale);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pan.isPressed()) {
            pan.release();
            return true;
        }
        return super.mouseReleased(toContentX(mouseX), toContentY(mouseY), button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isInViewport(mouseX, mouseY)) return false;
        if (super.mouseWheelMove(toContentX(mouseX), toContentY(mouseY), wheelDelta)) return true;
        if (wheelDelta == 0) return false;
        if (Screen.hasControlDown()) {
            if (!isZoomable() || zoomLocked) return false;
            zoomAround(mouseX, mouseY, stepped(wheelDelta > 0 ? 1 : -1));
            return true;
        }
        if (!overflowY()) return false;
        offsetY -= (float) (Math.signum(wheelDelta) * WHEEL_STEP / scale);
        placeContent();
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        UITheme.CANVAS.draw(graphics, mouseX, mouseY, x, y, w, h);
        if (painter == null) painter = new CanvasPainter();
        if (grid == null) grid = CanvasGrid.standard();
        enableScissor(graphics, x + 1, y + 1, w - 2, h - 2);
        painter.beginScreen(graphics, CanvasRect.of(x + 1, y + 1, w - 2, h - 2));
        grid.draw(painter, scale, -content.getSelfPositionX(), -content.getSelfPositionY(), x + 1, y + 1, w - 2, h - 2);
        boolean inside = isInViewport(mouseX, mouseY) && !pan.isPanning();
        var pose = graphics.pose();
        pose.pushPose();
        applyScale(graphics, x, y);
        super.drawInBackground(graphics, inside ? (int) toContentX(mouseX) : OUTSIDE, inside ? (int) toContentY(mouseY) : OUTSIDE, partialTicks);
        pose.popPose();
        drawEdges(graphics, x + 1, y + 1, w - 2, h - 2);
        graphics.disableScissor();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean inside = isInViewport(mouseX, mouseY) && !pan.isPanning();
        int x = getPositionX(), y = getPositionY();
        enableScissor(graphics, x, y, getSizeWidth(), getSizeHeight());
        var pose = graphics.pose();
        pose.pushPose();
        applyScale(graphics, x, y);
        super.drawInForeground(graphics, inside ? (int) toContentX(mouseX) : OUTSIDE, inside ? (int) toContentY(mouseY) : OUTSIDE, partialTicks);
        pose.popPose();
        graphics.disableScissor();
    }

    @OnlyIn(Dist.CLIENT)
    private void applyScale(GuiGraphics graphics, int x, int y) {
        if (scale == 1) return;
        var pose = graphics.pose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        pose.translate(-x, -y, 0);
    }

    @OnlyIn(Dist.CLIENT)
    private void enableScissor(GuiGraphics graphics, int x, int y, int w, int h) {
        var matrix = graphics.pose().last().pose();
        matrix.transform(scissorMin.set(x, y, 0, 1));
        matrix.transform(scissorMax.set(x + w, y + h, 0, 1));
        graphics.enableScissor(Math.round(scissorMin.x), Math.round(scissorMin.y), Math.round(scissorMax.x), Math.round(scissorMax.y));
    }

    @OnlyIn(Dist.CLIENT)
    private void drawEdges(GuiGraphics graphics, int x, int y, int w, int h) {
        int cx = content.getSelfPositionX(), cy = content.getSelfPositionY();
        boolean left = cx < 0, top = cy < 0;
        boolean right = (cx + content.getSizeWidth()) * scale > getSizeWidth() + 0.5f, bottom = (cy + content.getSizeHeight()) * scale > getSizeHeight() + 0.5f;
        for (int i = 0; i < EDGE; i++) {
            int color = (EDGE_ALPHA * (EDGE - i) / EDGE) << 24;
            if (left) graphics.fill(x + i, y, x + i + 1, y + h, color);
            if (right) graphics.fill(x + w - i - 1, y, x + w - i, y + h, color);
            if (top) graphics.fill(x, y + i, x + w, y + i + 1, color);
            if (bottom) graphics.fill(x, y + h - i - 1, x + w, y + h - i, color);
        }
    }
}
