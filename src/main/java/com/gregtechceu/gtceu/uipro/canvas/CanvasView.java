package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.animation.PixelSnap;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.view.PlanarView;
import com.gregtechceu.gtceu.uipro.window.CardHost;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 画布：可平移、缩放的无限平面（对应 LDLib2 {@code GraphView}），内容是只在客户端的图层（{@link CanvasLayer}），与项数无关的控件树。
 * 子元素是浮在内容上的悬浮层（底部居中）与右侧卡片位；视图、手势、缩放角与偏好见 {@link PlanarView}。
 */
public class CanvasView extends PlanarView {

    public static final String HELP_PAN = "gtceu.uipro.canvas.help.pan";
    public static final String HELP_ZOOM = "gtceu.uipro.canvas.help.zoom";

    private final List<CanvasLayer> layers = new ArrayList<>();
    @Nullable
    private Consumer<CanvasView> scene;
    private boolean sceneBuilt;
    @Nullable
    private CardHost floatingCard;
    private boolean contentBoundsValid;
    @Nullable
    private CanvasRect contentBounds;
    private final int[] minimap = new int[4];
    private final List<CanvasRect> selectedRects = new ArrayList<>(1);

    @Nullable
    private CanvasItem hovered;
    @Nullable
    private CanvasItem pressedItem;
    private boolean minimapDragging;
    @Nullable
    private ItemClickListener onItemClick;
    @Nullable
    private BackgroundClickListener onBackgroundClick;

    @FunctionalInterface
    public interface ItemClickListener {

        void onClick(CanvasItem item, int button, float worldX, float worldY);
    }

    @FunctionalInterface
    public interface BackgroundClickListener {

        void onClick(float worldX, float worldY, int button);
    }

    public CanvasView(String id, int width, int height) {
        super(id, width, height);
        layout(l -> l.column().justifyContent(AlignContent.FLEX_END).alignCenter().paddingAll(UISizes.DOCK_MARGIN));
    }

    // ==================== 配置 ====================

    public CanvasView setScene(Consumer<CanvasView> scene) {
        this.scene = scene;
        if (isInitialized() && isRemote()) rebuildScene();
        return this;
    }

    public void rebuildScene() {
        layers.clear();
        contentBoundsValid = false;
        hovered = null;
        pressedItem = null;
        sceneBuilt = true;
        if (scene != null) scene.accept(this);
    }

    public CanvasView addLayer(CanvasLayer layer) {
        layers.add(layer);
        contentBoundsValid = false;
        return this;
    }

    public void invalidateContentBounds() {
        contentBoundsValid = false;
    }

    public CanvasView setOnItemClick(@Nullable ItemClickListener onItemClick) {
        this.onItemClick = onItemClick;
        return this;
    }

    public CanvasView setOnBackgroundClick(@Nullable BackgroundClickListener onBackgroundClick) {
        this.onBackgroundClick = onBackgroundClick;
        return this;
    }

    public CanvasView addOverlay(Widget overlay) {
        addChild(overlay);
        return this;
    }

    public CanvasView addFloatingCard(CardHost card) {
        card.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).top(UISizes.DOCK_MARGIN).right(UISizes.DOCK_MARGIN));
        addChild(card);
        floatingCard = card;
        return this;
    }

    @Override
    public boolean hasMinimap() {
        return true;
    }

    @Override
    public boolean isMinimapAvailable() {
        return contentBounds() != null;
    }

    @Override
    protected int obstructedRight() {
        if (floatingCard == null || !floatingCard.isOpenOrRequested()) return 0;
        int cardWidth = floatingCard.getSizeWidth() > 0 ? floatingCard.getSizeWidth() : UISizes.POPUP_CONTENT_WIDTH + 2 * UISizes.POPUP_PADDING;
        return cardWidth + 2 * UISizes.DOCK_MARGIN - 2;
    }

    @Override
    @Nullable
    public CanvasRect contentBounds() {
        if (!contentBoundsValid) {
            CanvasRect bounds = null;
            for (var layer : layers) bounds = CanvasRect.union(bounds, layer.bounds());
            contentBounds = bounds;
            contentBoundsValid = true;
        }
        return contentBounds;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        if (isRemote() && !sceneBuilt) rebuildScene();
    }

    // ==================== 绘制 ====================

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawFrame(graphics, mouseX, mouseY);
        int vx = viewportX(), vy = viewportY(), vw = viewportWidth(), vh = viewportHeight();
        if (vw <= 0 || vh <= 0) return;
        if (!sceneBuilt) rebuildScene();
        prepareView();
        if (floatingCard != null) floatingCard.setMaxHeight(floatingCardMaxHeight());
        updateMinimapRect();

        boolean overOverlay = isOverChild(mouseX, mouseY);
        boolean inside = isInViewport(mouseX, mouseY) && !overOverlay && !isOverMinimap(mouseX, mouseY) && !isOverGrip(mouseX, mouseY) &&
                isPointerOver(mouseX, mouseY);
        hovered = inside && !isInteracting() ? pick(toWorldX(mouseX), toWorldY(mouseY)) : null;

        var pose = graphics.pose();
        enableClip(graphics);
        drawGrid(graphics);
        if (painter == null) painter = new CanvasPainter();
        float margin = UISizes.SLOT / scale;
        var visible = CanvasRect.of(offsetX - margin, offsetY - margin, vw / scale + 2 * margin, vh / scale + 2 * margin);
        float worldMouseX = inside ? toWorldX(mouseX) : Float.NaN, worldMouseY = inside ? toWorldY(mouseY) : Float.NaN;
        pose.pushPose();
        pose.translate(PixelSnap.snap(vx - offsetX * scale), PixelSnap.snap(vy - offsetY * scale), 0);
        pose.scale(scale, scale, 1);
        painter.begin(graphics, scale, lod(), visible, worldMouseX, worldMouseY, hovered);
        selectedRects.clear();
        for (var layer : layers) {
            layer.draw(painter, hovered);
            painter.flush();
            layer.collectSelected(selectedRects);
        }
        pose.popPose();
        for (var rect : selectedRects) {
            float fx = vx + (rect.x() - offsetX) * scale, fy = vy + (rect.y() - offsetY) * scale;
            int sx = Math.round(fx), sy = Math.round(fy);
            int sw = Math.round(rect.width() * scale), sh = Math.round(rect.height() * scale);
            pose.pushPose();
            pose.translate(PixelSnap.residual(fx), PixelSnap.residual(fy), 0);
            UITheme.drawSelection(graphics, sx, sy, sw, sh);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0, 0, OVERLAY_Z);
        if (minimap[2] > 0) drawMinimap(graphics);
        graphics.disableScissor();
        drawChildren(graphics, overOverlay ? mouseX : OUTSIDE, overOverlay ? mouseY : OUTSIDE, partialTicks);
        pose.popPose();
        drawGrip(graphics, mouseX, mouseY);
    }

    private int floatingCardMaxHeight() {
        int top = getPositionY() + UISizes.DOCK_MARGIN, bottom = getPositionY() + getSizeHeight() - UISizes.DOCK_MARGIN;
        for (var widget : widgets) {
            if (widget != floatingCard && widget.isVisible() && widget.getSizeHeight() > 0) {
                bottom = Math.min(bottom, widget.getPositionY() - UISizes.DOCK_MARGIN);
            }
        }
        return Math.max(UISizes.SLOT, bottom - top);
    }

    // ==================== 缩略图 ====================

    private void updateMinimapRect() {
        var bounds = contentBounds();
        if (!isMinimapShown() || bounds == null) {
            minimap[2] = 0;
            return;
        }
        int vw = viewportWidth(), vh = viewportHeight();
        int maxW = Mth.clamp(vw / 3, UISizes.CANVAS_MINIMAP_MIN_WIDTH, UISizes.CANVAS_MINIMAP_MAX_WIDTH);
        int maxH = Mth.clamp(vh / 3, UISizes.CANVAS_MINIMAP_MIN_HEIGHT, UISizes.CANVAS_MINIMAP_MAX_HEIGHT);
        float s = Math.min(maxW / Math.max(1, bounds.width()), maxH / Math.max(1, bounds.height()));
        int w = Math.max(8, Math.round(bounds.width() * s)) + 2, h = Math.max(8, Math.round(bounds.height() * s)) + 2;
        int right = viewportX() + unobstructedWidth() - (unobstructedWidth() < vw ? 0 : UISizes.CANVAS_MINIMAP_MARGIN);
        minimap[0] = right - w;
        minimap[1] = viewportY() + UISizes.CANVAS_MINIMAP_MARGIN;
        minimap[2] = w;
        minimap[3] = h;
    }

    private boolean isOverMinimap(double mouseX, double mouseY) {
        return minimap[2] > 0 && isMouseOver(minimap[0], minimap[1], minimap[2], minimap[3], mouseX, mouseY);
    }

    private float minimapScale(CanvasRect bounds) {
        return Math.min((minimap[2] - 2) / Math.max(1, bounds.width()), (minimap[3] - 2) / Math.max(1, bounds.height()));
    }

    @OnlyIn(Dist.CLIENT)
    private void drawMinimap(GuiGraphics graphics) {
        var bounds = contentBounds();
        if (bounds == null || painter == null) return;
        int mx = minimap[0], my = minimap[1], mw = minimap[2], mh = minimap[3];
        graphics.fill(mx, my, mx + mw, my + mh, UITheme.CANVAS_MINIMAP_FILL);
        UITheme.drawOutline(graphics, mx, my, mw, mh, UITheme.CANVAS_MINIMAP_BORDER);
        float s = minimapScale(bounds);
        float ox = mx + 1 + ((mw - 2) - bounds.width() * s) / 2, oy = my + 1 + ((mh - 2) - bounds.height() * s) / 2;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(ox - bounds.x() * s, oy - bounds.y() * s, 0);
        pose.scale(s, s, 1);
        painter.begin(graphics, s, CanvasLod.BLOCK, bounds.inflate(1), Float.NaN, Float.NaN, null);
        for (var layer : layers) layer.drawMinimap(painter);
        painter.flush();
        pose.popPose();
        float right = offsetX + viewportWidth() / scale, bottom = offsetY + viewportHeight() / scale;
        int x0 = Mth.clamp(Math.round(ox + (offsetX - bounds.x()) * s), mx, mx + mw - 2);
        int y0 = Mth.clamp(Math.round(oy + (offsetY - bounds.y()) * s), my, my + mh - 2);
        int x1 = Mth.clamp(Math.round(ox + (right - bounds.x()) * s), x0 + 2, mx + mw);
        int y1 = Mth.clamp(Math.round(oy + (bottom - bounds.y()) * s), y0 + 2, my + mh);
        UITheme.drawOutline(graphics, x0, y0, x1 - x0, y1 - y0, UITheme.CANVAS_MINIMAP_VIEWPORT);
    }

    private void navigateMinimap(double mouseX, double mouseY) {
        var bounds = contentBounds();
        if (minimap[2] <= 0 || bounds == null) return;
        float s = minimapScale(bounds);
        float ox = minimap[0] + 1 + ((minimap[2] - 2) - bounds.width() * s) / 2, oy = minimap[1] + 1 + ((minimap[3] - 2) - bounds.height() * s) / 2;
        centerOn(bounds.x() + (float) (mouseX - ox) / s, bounds.y() + (float) (mouseY - oy) / s, false);
    }

    // ==================== 命中、提示、EMI ====================

    @Nullable
    private CanvasItem pick(float worldX, float worldY) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            var item = layers.get(i).pick(worldX, worldY);
            if (item != null) return item;
        }
        return null;
    }

    @Nullable
    public CanvasItem itemAt(double mouseX, double mouseY) {
        if (!isInViewport(mouseX, mouseY) || isOverChild(mouseX, mouseY) || isOverMinimap(mouseX, mouseY) || isOverGrip(mouseX, mouseY)) return null;
        return pick(toWorldX(mouseX), toWorldY(mouseY));
    }

    @Nullable
    public CanvasItem getHovered() {
        return hovered;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (gui == null || gui.getModularUIGui() == null || isInteracting() || isOverGrip(mouseX, mouseY) || hovered == null) return;
        var tooltip = hovered.tooltip();
        if (!tooltip.isEmpty()) gui.getModularUIGui().setHoverTooltip(tooltip, ItemStack.EMPTY, null, null);
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        if (!isVisible()) return null;
        if (isOverChild(mouseX, mouseY)) return super.getXEIIngredientOverMouse(mouseX, mouseY);
        var item = itemAt(mouseX, mouseY);
        return item == null ? null : item.ingredient();
    }

    // ==================== 鼠标 ====================

    @Override
    @OnlyIn(Dist.CLIENT)
    protected boolean onViewPress(double mouseX, double mouseY, int button) {
        if (isOverChild(mouseX, mouseY)) return true;
        cancelViewAnimation();
        if (isOverMinimap(mouseX, mouseY)) {
            if (button == 0) {
                minimapDragging = true;
                capturePointer(0);
                navigateMinimap(mouseX, mouseY);
            }
            return true;
        }
        super.onViewPress(mouseX, mouseY, button);
        pressedItem = pick(toWorldX(mouseX), toWorldY(mouseY));
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (minimapDragging) {
            navigateMinimap(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (minimapDragging) {
            minimapDragging = false;
            releasePointer();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void onViewRelease(double mouseX, double mouseY, int button, boolean click) {
        var item = pressedItem;
        pressedItem = null;
        super.onViewRelease(mouseX, mouseY, button, click);
        if (!click || !isInViewport(mouseX, mouseY)) return;
        float worldX = toWorldX(mouseX), worldY = toWorldY(mouseY);
        var released = pick(worldX, worldY);
        if (item != null && released == item) {
            if (onItemClick != null) {
                onItemClick.onClick(item, button, worldX, worldY);
                playButtonClickSound();
            }
        } else if (item == null && released == null && onBackgroundClick != null) {
            onBackgroundClick.onClick(worldX, worldY, button);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected boolean onViewWheel(double mouseX, double mouseY, double wheelDelta) {
        if (isOverMinimap(mouseX, mouseY) || isOverChild(mouseX, mouseY)) return true;
        return super.onViewWheel(mouseX, mouseY, wheelDelta);
    }
}
