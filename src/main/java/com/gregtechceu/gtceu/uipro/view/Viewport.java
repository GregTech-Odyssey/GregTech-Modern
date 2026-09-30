package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.ILayoutHost;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.UIInput;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.LockedScrollerSizes;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 所有视图（2D 画布、流程图、3D 场景）的基类：外框与裁剪、按下/拖动/松开手势（带阈值与指针捕获）、缩放角与尺寸锁定、
 * 视图偏好（按视图 id 与玩家记忆缩放、缩略图开关，关界面时保存）。视图状态只在客户端，两端控件树只有视口与它的子元素。
 */
public abstract class Viewport extends UIElement implements ViewController {

    public static final String GRIP_RESIZE = "gtceu.uipro.canvas.resize";
    public static final String GRIP_LOCK = "gtceu.uipro.canvas.lock";
    public static final String GRIP_LOCKED = "gtceu.uipro.canvas.locked";
    public static final String GRIP_UNLOCK = "gtceu.uipro.canvas.unlock";
    protected static final int OVERLAY_Z = 310;
    private static final int MIN_SIZE = UISizes.CANVAS_MIN_SIZE;

    protected final String id;
    private String zoomKey;
    protected final DragGesture gesture = new DragGesture(this);
    private int frame = 1;
    private boolean drawFrame = true;
    private boolean rememberView = true;
    private int preferredWidth, preferredHeight;
    private int userWidth = -1, userHeight = -1;
    private int lockedWidth = -1, lockedHeight = -1;
    private boolean resizable = true;
    @Nullable
    private int[] screenFill;
    private boolean resizing;
    private double resizeStartX, resizeStartY;
    private int resizeStartWidth, resizeStartHeight;
    private int resizeMaxWidth = Integer.MAX_VALUE, resizeMaxHeight = Integer.MAX_VALUE;
    private boolean minimapShown;
    private boolean prefsLoaded;

    protected Viewport(String id, int width, int height) {
        this.id = id;
        this.zoomKey = id;
        this.preferredWidth = width;
        this.preferredHeight = height;
        setClipChildren(true);
        applySize();
        listenUIClose();
    }

    public String getViewId() {
        return id;
    }

    public Viewport setFrame(boolean drawFrame, int inset) {
        this.drawFrame = drawFrame;
        this.frame = Math.max(0, inset);
        return this;
    }

    public Viewport setZoomKey(String zoomKey) {
        this.zoomKey = zoomKey;
        return this;
    }

    public String zoomKey() {
        return zoomKey;
    }

    public Viewport setRememberView(boolean rememberView) {
        this.rememberView = rememberView;
        return this;
    }

    public boolean isRememberView() {
        return rememberView;
    }

    public Viewport setResizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    public boolean isResizable() {
        return resizable;
    }

    public int viewportX() {
        return getPositionX() + frame;
    }

    public int viewportY() {
        return getPositionY() + frame;
    }

    public int viewportWidth() {
        return Math.max(0, getSizeWidth() - 2 * frame);
    }

    public int viewportHeight() {
        return Math.max(0, getSizeHeight() - 2 * frame);
    }

    public boolean isInViewport(double mouseX, double mouseY) {
        return isMouseOver(viewportX(), viewportY(), viewportWidth(), viewportHeight(), mouseX, mouseY);
    }

    @Override
    public int clipX() {
        return viewportX();
    }

    @Override
    public int clipY() {
        return viewportY();
    }

    @Override
    public int clipWidth() {
        return viewportWidth();
    }

    @Override
    public int clipHeight() {
        return viewportHeight();
    }

    public boolean isOverChild(double mouseX, double mouseY) {
        for (var widget : widgets) {
            if (widget.isVisible() && widget.isMouseOverElement(mouseX, mouseY)) return true;
        }
        return false;
    }

    protected void applySize() {
        int w = lockedWidth > 0 ? lockedWidth : userWidth > 0 ? userWidth : preferredWidth;
        int h = lockedHeight > 0 ? lockedHeight : userHeight > 0 ? userHeight : preferredHeight;
        layout(l -> l.size(w, h));
    }

    public Viewport setPreferredSize(int width, int height) {
        this.preferredWidth = width;
        this.preferredHeight = height;
        applySize();
        return this;
    }

    public int preferredWidth() {
        return preferredWidth;
    }

    public int preferredHeight() {
        return preferredHeight;
    }

    public Viewport fillScreen(int reservedWidth, int reservedHeight, int maxWidth, int maxHeight) {
        this.screenFill = new int[] { reservedWidth, reservedHeight, maxWidth, maxHeight };
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    private void applyScreenFill() {
        if (screenFill == null) return;
        var area = MachineWindow.clientScreenArea();
        int w = area.width() - screenFill[0];
        int h = area.height() - screenFill[1];
        preferredWidth = Mth.clamp(w, preferredWidth, Math.max(preferredWidth, screenFill[2]));
        preferredHeight = Mth.clamp(h, preferredHeight, Math.max(preferredHeight, screenFill[3]));
        applySize();
    }

    public boolean isLocked() {
        return lockedWidth > 0 && lockedHeight > 0;
    }

    @OnlyIn(Dist.CLIENT)
    private void loadLockedSize() {
        int w = LockedScrollerSizes.width(id), h = LockedScrollerSizes.height(id);
        var window = Minecraft.getInstance().getWindow();
        boolean fits = w >= MIN_SIZE && h >= MIN_SIZE && w <= window.getGuiScaledWidth() && h <= window.getGuiScaledHeight();
        lockedWidth = fits ? w : -1;
        lockedHeight = fits ? h : -1;
        applySize();
    }

    @Override
    public void initWidget() {
        super.initWidget();
        if (!isRemote()) return;
        applyScreenFill();
        if (resizable) loadLockedSize();
        loadPrefs();
    }

    @OnlyIn(Dist.CLIENT)
    private void loadPrefs() {
        if (prefsLoaded) return;
        prefsLoaded = true;
        if (!rememberView) return;
        if (hasMinimap()) minimapShown = ViewPrefs.minimap(id, minimapShown);
        float zoom = ViewPrefs.zoom(zoomKey);
        if (!Float.isNaN(zoom)) restoreZoom(zoom);
    }

    @Override
    protected void onUIClosed() {
        if (gui == null || !isRemote() || !rememberView) return;
        float zoom = rememberedZoom();
        if (!Float.isNaN(zoom)) ViewPrefs.putZoom(zoomKey, zoom);
    }

    protected void restoreZoom(float zoom) {}

    protected float rememberedZoom() {
        return Float.NaN;
    }

    @Override
    public boolean isMinimapShown() {
        return minimapShown;
    }

    @Override
    public void setMinimapShown(boolean shown) {
        this.minimapShown = shown;
        if (isRemote() && rememberView && hasMinimap()) ViewPrefs.putMinimap(id, shown);
    }

    public void setMinimapDefault(boolean shown) {
        this.minimapShown = shown;
    }

    @OnlyIn(Dist.CLIENT)
    protected void drawFrame(GuiGraphics graphics, int mouseX, int mouseY) {
        if (drawFrame) UITheme.CANVAS.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    @OnlyIn(Dist.CLIENT)
    protected void drawChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean masked = gesture.isDragging() || resizing;
        super.drawInBackground(graphics, masked ? OUTSIDE : mouseX, masked ? OUTSIDE : mouseY, partialTicks);
    }

    @OnlyIn(Dist.CLIENT)
    protected void drawGrip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!resizable) return;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, OVERLAY_Z + 10);
        UITheme.drawResizeGrip(graphics, getPositionX() + getSizeWidth(), getPositionY() + getSizeHeight(),
                resizing || isOverGrip(mouseX, mouseY), isLocked());
        pose.popPose();
    }

    @OnlyIn(Dist.CLIENT)
    protected boolean isInteracting() {
        abandonStaleInteraction();
        return gesture.isDragging() || resizing;
    }

    @OnlyIn(Dist.CLIENT)
    private void abandonStaleInteraction() {
        if (!gesture.isPressed() && !resizing || UIInput.captured() == this) return;
        endInteraction();
    }

    @OnlyIn(Dist.CLIENT)
    private void endInteraction() {
        if (gesture.isPressed()) gesture.release();
        if (resizing) {
            resizing = false;
            releasePointer();
            var host = ILayoutHost.of(this);
            if (host != null) host.endInteractiveResize();
        }
    }

    @Override
    public void setGui(com.lowdragmc.lowdraglib.gui.modular.ModularUI gui) {
        if (gui == null && isRemote() && (gesture.isPressed() || resizing)) endInteraction();
        super.setGui(gui);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean masked = isInteracting();
        super.drawInForeground(graphics, masked ? OUTSIDE : mouseX, masked ? OUTSIDE : mouseY, partialTicks);
        if (gui == null || gui.getModularUIGui() == null || isInteracting() || !isOverGrip(mouseX, mouseY)) return;
        var lines = isLocked() ?
                List.<Component>of(Component.translatable(GRIP_LOCKED), Component.translatable(GRIP_UNLOCK).withStyle(ChatFormatting.GRAY)) :
                List.<Component>of(Component.translatable(GRIP_RESIZE), Component.translatable(GRIP_LOCK).withStyle(ChatFormatting.GRAY));
        gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
    }

    public boolean isOverGrip(double mouseX, double mouseY) {
        if (!resizable) return false;
        int right = getPositionX() + getSizeWidth(), bottom = getPositionY() + getSizeHeight();
        return mouseX >= right - UITheme.RESIZE_GRIP_SIZE && mouseX < right && mouseY >= bottom - UITheme.RESIZE_GRIP_SIZE && mouseY < bottom;
    }

    @OnlyIn(Dist.CLIENT)
    protected abstract boolean onViewPress(double mouseX, double mouseY, int button);

    @OnlyIn(Dist.CLIENT)
    protected abstract void onViewDrag(double mouseX, double mouseY, int button, double dragX, double dragY);

    @OnlyIn(Dist.CLIENT)
    protected abstract void onViewRelease(double mouseX, double mouseY, int button, boolean click);

    @OnlyIn(Dist.CLIENT)
    protected abstract boolean onViewWheel(double mouseX, double mouseY, double wheelDelta);

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isActive() || !isPointerOver(mouseX, mouseY)) return false;
        if (isOverGrip(mouseX, mouseY)) {
            if (button == 1) toggleLock();
            else if (button == 0 && !isLocked()) startResize(mouseX, mouseY);
            return true;
        }
        if (!isInViewport(mouseX, mouseY)) return false;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return onViewPress(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        abandonStaleInteraction();
        if (resizing) {
            resizeTo(mouseX, mouseY);
            return true;
        }
        if (gesture.isPressed()) {
            onViewDrag(mouseX, mouseY, button, dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizing) {
            resizing = false;
            releasePointer();
            var host = ILayoutHost.of(this);
            if (host != null) host.endInteractiveResize();
            return true;
        }
        if (gesture.isPressed()) {
            if (gesture.button() != button) return true;
            boolean click = gesture.release();
            onViewRelease(mouseX, mouseY, button, click);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isVisible() || !isActive() || !isInViewport(mouseX, mouseY) || !isPointerOver(mouseX, mouseY)) return false;
        if (super.mouseWheelMove(mouseX, mouseY, wheelDelta)) return true;
        return wheelDelta != 0 && onViewWheel(mouseX, mouseY, wheelDelta);
    }

    @OnlyIn(Dist.CLIENT)
    private void startResize(double mouseX, double mouseY) {
        resizing = true;
        capturePointer(0);
        resizeStartX = mouseX;
        resizeStartY = mouseY;
        resizeStartWidth = getSizeWidth();
        resizeStartHeight = getSizeHeight();
        var host = ILayoutHost.of(this);
        int allowX = host == null ? Integer.MAX_VALUE : host.resizeAllowance(false);
        int allowY = host == null ? Integer.MAX_VALUE : host.resizeAllowance(true);
        resizeMaxWidth = allowX == Integer.MAX_VALUE ? Integer.MAX_VALUE : resizeStartWidth + Math.max(0, allowX);
        resizeMaxHeight = allowY == Integer.MAX_VALUE ? Integer.MAX_VALUE : resizeStartHeight + Math.max(0, allowY);
        if (host != null) host.beginInteractiveResize();
    }

    @OnlyIn(Dist.CLIENT)
    private void resizeTo(double mouseX, double mouseY) {
        userWidth = Mth.clamp(resizeStartWidth + (int) (mouseX - resizeStartX), MIN_SIZE, Math.max(MIN_SIZE, resizeMaxWidth));
        userHeight = Mth.clamp(resizeStartHeight + (int) (mouseY - resizeStartY), MIN_SIZE, Math.max(MIN_SIZE, resizeMaxHeight));
        applySize();
    }

    @OnlyIn(Dist.CLIENT)
    private void toggleLock() {
        if (isLocked()) {
            lockedWidth = lockedHeight = -1;
            LockedScrollerSizes.unlock(id);
        } else {
            lockedWidth = userWidth = Math.max(MIN_SIZE, getSizeWidth());
            lockedHeight = userHeight = Math.max(MIN_SIZE, getSizeHeight());
            LockedScrollerSizes.lock(id, lockedWidth, lockedHeight);
        }
        playButtonClickSound();
        applySize();
    }
}
