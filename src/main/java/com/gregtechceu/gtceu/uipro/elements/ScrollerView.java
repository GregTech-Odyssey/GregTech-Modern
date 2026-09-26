package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ILayoutHost;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.LockedScrollerSizes;

import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.util.MeasureFunc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 纵向滚动视图，对应 LDLib2 {@code ScrollerView}。滚动逻辑沿用 LDLib1 的 {@link DraggableScrollableWidgetGroup}，
 * 内容放在一个纵向 {@link UIElement}（自成一棵布局树）里；视图本身是父布局里的 {@link ILayoutItem}，尺寸由测量函数给出。
 * <ul>
 * <li>宽度：父元素拉伸时取分配的宽度，否则取首选宽度（构造参数）；{@link #adaptiveWidth()} 时跟随内容。</li>
 * <li>高度：默认取首选高度；{@link #adaptiveHeight(int)} 时跟随内容、最高到上限，超出才滚动（对应 LDLib2 同名样式）。</li>
 * <li>滚动条按 {@link ScrollDisplay} 显示（默认内容放不下才显示），不显示时不占位置，内容铺满整个宽度。</li>
 * <li>右下角有拖拽缩放角，只沿滚动方向缩放（纵向滚动区只改高度；adaptive 时改高度上限），
 * 整个界面不超过屏幕的 2/3（{@link ILayoutHost#resizeAllowance}）。拖拽中窗口位置钉住，松手后动画回到居中；
 * 外层布局随之重排。拖出来的高度只属于这一个打开的界面、只在客户端。</li>
 * <li>缩放角右键锁定当前高度（缩放角换成直角样式），按 {@link #id} 存在客户端本地（{@link LockedScrollerSizes}），
 * 重开界面时沿用；超出屏幕上限等无法应用时回到默认。锁定后不能拖，再右键解锁。</li>
 * </ul>
 * 内容列按 flexbox 默认拉伸子元素，行内再用 {@code flex(1)} 分宽度，就能随滚动条显隐自动铺满；
 * 不要在建界面时按 {@link #getContentWidth()} 算好宽度写死。视口外要与内容对齐的元素用 {@link #setOnContentWidthChanged}。
 */
public class ScrollerView extends DraggableScrollableWidgetGroup implements ILayoutItem {

    public static final int SCROLL_BAR_WIDTH = 8;
    public static final int SCROLL_BAR_MARGIN = 2;
    /** 滚动条连同与内容的间距所占宽度。 */
    public static final int SCROLL_BAR_SPACE = SCROLL_BAR_WIDTH + SCROLL_BAR_MARGIN;
    /// 拖拽缩放时视口的最小高度
    private static final int MIN_RESIZE_HEIGHT = UISizes.SLOT;

    /** 滚动条显示方式，对应 LDLib2 {@code ScrollDisplay}。 */
    public enum ScrollDisplay {
        /** 内容放不下时显示。 */
        AUTO,
        ALWAYS,
        NEVER
    }

    public static final String GRIP_RESIZE = "gtceu.uipro.scroller.resize";
    public static final String GRIP_LOCK = "gtceu.uipro.scroller.lock";
    public static final String GRIP_LOCKED = "gtceu.uipro.scroller.locked";
    public static final String GRIP_UNLOCK = "gtceu.uipro.scroller.unlock";

    /** 固定的 id（按界面里的位置取，如 {@code wireless.networks}），锁定的尺寸按它保存。 */
    private final String id;
    private final Content content;
    private final LayoutStyle layoutStyle = LayoutStyle.fixed(LayoutStyle.AUTO, LayoutStyle.AUTO, () -> UIElement.markLayoutDirty(this));
    private int preferredWidth;
    private int preferredHeight;
    private ScrollDisplay verticalScrollDisplay = ScrollDisplay.AUTO;
    private boolean adaptiveWidth;
    private int adaptiveMaxHeight = -1;
    /// 本次打开界面里拖出来的高度（不保存），-1 为没拖过
    private int userHeight = -1;
    /// 玩家锁定的高度（客户端本地保存，重开界面沿用），-1 为没锁定或无法应用
    private int lockedHeight = -1;
    private boolean resizable = true;
    @Nullable
    private IntConsumer onContentWidthChanged;
    private int notifiedContentWidth = -1;
    /// 正在为测量 / 调整而改内容宽度：由此引起的内容尺寸变化不再回头通知
    private boolean measuring;
    private boolean updating;
    // 拖拽缩放角
    private boolean resizing;
    private double resizeStartY;
    private int resizeStartHeight;
    private int resizeMaxHeight = Integer.MAX_VALUE;

    public ScrollerView(String id, int width, int height) {
        this(id, width, height, 0);
    }

    /**
     * @param id     固定的 id（按界面里的位置取），锁定的尺寸按它保存
     * @param width  首选宽度
     * @param height 首选高度（{@link #adaptiveHeight} 时为初始值）
     */
    public ScrollerView(String id, int width, int height, int gap) {
        super(0, 0, width, height);
        this.id = id;
        if (isRemote()) lockedHeight = applicableLockedHeight(id);
        this.preferredWidth = width;
        this.preferredHeight = height;
        setScrollWheelDirection(ScrollWheelDirection.VERTICAL);
        setYScrollBarWidth(0);
        setYBarStyle(UITheme.SCROLL_TRACK, UITheme.SCROLL_THUMB);
        setDraggable(false);
        setScrollable(true);
        setUseScissor(true);
        content = new Content();
        // 加内容前先按有滚动条算宽度：还按固定宽度建子元素的代码不会伸进滚动条；排布后再按需要放宽
        content.layout(l -> l.column().width(width - SCROLL_BAR_SPACE).gapAll(gap));
        super.addWidget(content);
    }

    /** 内容列：自成一棵布局树，排布完成后通知滚动区重新测量。 */
    private final class Content extends UIElement {

        private int lastWidth = -1, lastHeight = -1;

        @Override
        protected void onLayoutFinished() {
            if (getSizeWidth() == lastWidth && getSizeHeight() == lastHeight) return;
            lastWidth = getSizeWidth();
            lastHeight = getSizeHeight();
            onContentResized();
        }
    }

    // ==================== 配置 ====================

    /** 内容列当前宽度（显示滚动条时已扣除）。会随滚动条显隐、拖拽变化，见类注释。 */
    public int getContentWidth() {
        return content.getContentWidth();
    }

    public boolean isVerticalScrollBarShown() {
        return yBarWidth > 0;
    }

    public ScrollerView verticalScrollDisplay(ScrollDisplay display) {
        this.verticalScrollDisplay = display;
        relayout();
        return this;
    }

    /**
     * 高度跟随内容，最高 {@code maxHeight}，超出才滚动。内容高度只在客户端准确（文字尺寸在客户端测量），
     * 因此两端尺寸可能不同，但控件树一致，不影响同步。
     */
    public ScrollerView adaptiveHeight(int maxHeight) {
        this.adaptiveMaxHeight = Math.max(1, maxHeight);
        relayout();
        return this;
    }

    /** 宽度跟随内容：内容列按子元素撑开，视口 = 内容宽度，显示滚动条时再加 {@link #SCROLL_BAR_SPACE}。 */
    public ScrollerView adaptiveWidth() {
        this.adaptiveWidth = true;
        content.layout(l -> l.widthAuto());
        relayout();
        return this;
    }

    /** 是否显示右下角的拖拽缩放角（默认显示）；不能缩放时也不套用锁定的高度。 */
    public ScrollerView setResizable(boolean resizable) {
        this.resizable = resizable;
        if (!resizable && (lockedHeight > 0 || userHeight > 0)) {
            lockedHeight = userHeight = -1;
            relayout();
        }
        return this;
    }

    /** 内容列宽度随滚动条显隐、拖拽变化后回调（参数为新宽度），供视口外需要与内容对齐的元素跟随。 */
    public ScrollerView setOnContentWidthChanged(@Nullable IntConsumer listener) {
        this.onContentWidthChanged = listener;
        return this;
    }

    public ScrollerView layoutContent(Consumer<LayoutStyle> layout) {
        content.layout(layout);
        return this;
    }

    public ScrollerView addScrollViewChild(Widget child) {
        content.addWidget(child);
        return this;
    }

    /** 清空内容（不要对滚动区本身调 {@code clearAllWidgets}，那会把内容列一起删掉）。 */
    public ScrollerView clearScrollViewChildren() {
        content.clearAllWidgets();
        return this;
    }

    // ==================== 布局 ====================

    @Override
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    @Override
    public MeasureFunc getLayoutMeasure() {
        return (known, available) -> {
            float width = Float.isNaN(known.width) ? naturalWidth() : known.width;
            float height = Float.isNaN(known.height) ? naturalHeight(Math.round(width)) : known.height;
            return new FloatSize(width, height);
        };
    }

    /** 高度上限（adaptive）或高度，优先级：锁定的、本次拖出来的、默认值。 */
    private int heightLimit() {
        if (lockedHeight > 0) return lockedHeight;
        if (userHeight > 0) return userHeight;
        return adaptiveMaxHeight > 0 ? adaptiveMaxHeight : preferredHeight;
    }

    /** 客户端：读取锁定的高度，超出屏幕上限（界面不超过屏幕 2/3）或过小时不应用，回到默认。 */
    @OnlyIn(Dist.CLIENT)
    private static int applicableLockedHeight(String id) {
        int height = LockedScrollerSizes.height(id);
        if (height < MIN_RESIZE_HEIGHT) return -1;
        int screen = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return height <= screen * UISizes.MAX_WINDOW_SCREEN_RATIO ? height : -1;
    }

    public boolean isLocked() {
        return lockedHeight > 0;
    }

    private int naturalWidth() {
        if (!adaptiveWidth) return preferredWidth;
        boolean bar = verticalScrollDisplay == ScrollDisplay.ALWAYS ||
                (verticalScrollDisplay == ScrollDisplay.AUTO && adaptiveMaxHeight > 0 && content.getSizeHeight() > heightLimit());
        return content.getSizeWidth() + (bar ? SCROLL_BAR_SPACE : 0);
    }

    private int naturalHeight(int width) {
        if (adaptiveMaxHeight <= 0) return heightLimit();
        int limit = heightLimit();
        int height = contentHeightAt(width);
        if (verticalScrollDisplay == ScrollDisplay.ALWAYS || (verticalScrollDisplay == ScrollDisplay.AUTO && height > limit)) {
            height = contentHeightAt(width - SCROLL_BAR_SPACE);
        }
        return Math.min(height, limit);
    }

    /** 视口宽 {@code width}（不含滚动条）时内容的高度。 */
    private int contentHeightAt(int width) {
        if (adaptiveWidth) return content.getSizeHeight();
        int contentWidth = Math.max(0, width);
        measuring = true;
        try {
            content.layout(l -> l.width(contentWidth));
            return content.getSizeHeight();
        } finally {
            measuring = false;
        }
    }

    /** 尺寸相关的配置或内容变了：让父布局重新测量；不在 UIElement 里时自己定尺寸。 */
    private void relayout() {
        if (getParent() instanceof UIElement parent && parent.widgets.contains(this)) {
            UIElement.markLayoutDirty(this);
        } else {
            setSize(new Size(naturalWidth(), naturalHeight(naturalWidth())));
        }
        updateScrollArea();
    }

    private void onContentResized() {
        if (measuring || updating) return;
        relayout();
    }

    @Override
    public void setSize(Size size) {
        super.setSize(size);
        updateScrollArea();
    }

    /** 按当前视口尺寸决定滚动条显隐、内容宽度，并刷新滚动范围。 */
    private void updateScrollArea() {
        if (updating || content == null) return;
        updating = true;
        try {
            int width = getSizeWidth(), height = getSizeHeight();
            boolean shown = switch (verticalScrollDisplay) {
                case ALWAYS -> true;
                case NEVER -> false;
                case AUTO -> contentHeightAt(width) > height;
            };
            int barWidth = shown ? SCROLL_BAR_WIDTH : 0;
            if (yBarWidth != barWidth) setYScrollBarWidth(barWidth);
            if (!adaptiveWidth) {
                int contentWidth = Math.max(0, width - (shown ? SCROLL_BAR_SPACE : 0));
                measuring = true;
                try {
                    content.layout(l -> l.width(contentWidth));
                } finally {
                    measuring = false;
                }
                if (contentWidth != notifiedContentWidth) {
                    notifiedContentWidth = contentWidth;
                    if (onContentWidthChanged != null) onContentWidthChanged.accept(contentWidth);
                }
            }
            // 父类 setSize 只会把滚动范围往大改、从不缩小；视口变化后把范围重置回视口，否则会多出一截空白可滚动区域
            maxHeight = height - xBarHeight;
            computeMax();
        } finally {
            updating = false;
        }
    }

    // ==================== 视口裁剪 ====================
    //
    // LDLib 1.0.50 的 WidgetGroup.isMouseOverElement 在鼠标落在任一可见子控件上时也返回 true——滚出视口的子控件也算。
    // DraggableScrollableWidgetGroup 正是靠 isMouseOverElement 决定是否把悬停提示、点击、滚轮交给子控件，于是视口外
    // （被裁掉、看不见）的槽位、行照样弹提示、能被点到。这里把命中范围收回到视口本身，并在视口外把鼠标挪到界外再交给子控件。

    /// 视口外时交给子控件的鼠标坐标：离开任何控件
    private static final int OUTSIDE = -100000;

    private boolean isInViewport(double mouseX, double mouseY) {
        return isMouseOver(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY);
    }

    private double viewportX(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY) ? mouseX : OUTSIDE;
    }

    private double viewportY(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY) ? mouseY : OUTSIDE;
    }

    /** 只看视口本身，不看子控件（见上）。悬停提示、点击、滚轮、按键都经它判定。 */
    @Override
    public boolean isMouseOverElement(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY);
    }

    @Override
    public @Nullable Widget getHoverElement(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY) ? super.getHoverElement(mouseX, mouseY) : null;
    }

    /** EMI 查看鼠标下的物品：视口外的内容不算。 */
    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        return isInViewport(mouseX, mouseY) ? super.getXEIIngredientOverMouse(mouseX, mouseY) : null;
    }

    /** EMI 拖放目标：裁到视口内，完全在视口外的去掉——否则能把物品拖进看不见的虚拟槽。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public List<Target> getPhantomTargets(Object ingredient) {
        var targets = super.getPhantomTargets(ingredient);
        if (targets.isEmpty()) return targets;
        int left = getPositionX(), top = getPositionY(), right = left + getSizeWidth(), bottom = top + getSizeHeight();
        var clipped = new ArrayList<Target>(targets.size());
        for (var target : targets) {
            var area = target.getArea();
            int x0 = Math.max(left, area.getX()), y0 = Math.max(top, area.getY());
            int x1 = Math.min(right, area.getX() + area.getWidth()), y1 = Math.min(bottom, area.getY() + area.getHeight());
            if (x1 <= x0 || y1 <= y0) continue;
            var rect = new Rect2i(x0, y0, x1 - x0, y1 - y0);
            clipped.add(new Target() {

                @Override
                public Rect2i getArea() {
                    return rect;
                }

                @Override
                public void accept(Object value) {
                    target.accept(value);
                }
            });
        }
        return clipped;
    }

    // ==================== 拖拽缩放角 ====================

    private boolean isOverGrip(double mouseX, double mouseY) {
        if (!resizable) return false;
        int right = getPositionX() + getSizeWidth(), bottom = getPositionY() + getSizeHeight();
        return mouseX >= right - UITheme.RESIZE_GRIP_SIZE && mouseX < right && mouseY >= bottom - UITheme.RESIZE_GRIP_SIZE && mouseY < bottom;
    }

    /** 纵向滚动区只能纵向缩放：adaptive 时改高度上限，否则改首选高度；夹在最小高度与屏幕上限之间。 */
    private void resizeTo(int height) {
        int clamped = Math.max(MIN_RESIZE_HEIGHT, Math.min(height, resizeMaxHeight));
        userHeight = clamped;
        relayout();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && isOverGrip(mouseX, mouseY)) {
            toggleLock();
            return true;
        }
        // 锁定后不能拖，右键解锁后才能调整
        if (button == 0 && isOverGrip(mouseX, mouseY) && isLocked()) return true;
        if (button == 0 && isOverGrip(mouseX, mouseY)) {
            resizing = true;
            resizeStartY = mouseY;
            resizeStartHeight = getSizeHeight();
            // 整个界面不超过屏幕 2/3：这次拖拽最多再长外壳给的余量（已超过时只能缩小）
            var host = ILayoutHost.of(this);
            int allowance = host == null ? Integer.MAX_VALUE : host.resizeAllowance(true);
            resizeMaxHeight = allowance == Integer.MAX_VALUE ? Integer.MAX_VALUE : resizeStartHeight + Math.max(0, allowance);
            if (host != null) host.beginInteractiveResize();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 右键：锁定当前高度（本地保存，重开界面沿用）／解锁（回到默认或本次拖出来的高度）。 */
    @OnlyIn(Dist.CLIENT)
    private void toggleLock() {
        if (isLocked()) {
            lockedHeight = -1;
            LockedScrollerSizes.unlock(id);
        } else {
            lockedHeight = Math.max(MIN_RESIZE_HEIGHT, getSizeHeight());
            userHeight = lockedHeight;
            LockedScrollerSizes.lock(id, lockedHeight);
        }
        playButtonClickSound();
        relayout();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (resizing) {
            resizeTo(resizeStartHeight + (int) (mouseY - resizeStartY));
            return true;
        }
        return super.mouseDragged(viewportX(mouseX, mouseY), viewportY(mouseX, mouseY), button, deltaX, deltaY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizing) {
            resizing = false;
            var host = ILayoutHost.of(this);
            if (host != null) host.endInteractiveResize();
            return true;
        }
        return super.mouseReleased(viewportX(mouseX, mouseY), viewportY(mouseX, mouseY), button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 鼠标在视口外时，内容按"鼠标不在任何子控件上"绘制：露出一半的槽、按钮不因鼠标停在被裁掉的那一半而高亮
        boolean inside = isInViewport(mouseX, mouseY);
        super.drawInBackground(graphics, inside ? mouseX : OUTSIDE, inside ? mouseY : OUTSIDE, partialTicks);
        if (!resizable) return;
        var pose = graphics.pose();
        pose.pushPose();
        // 画在内容之上（物品图标 z 约 150）
        pose.translate(0, 0, UITheme.OVERLAY_Z);
        UITheme.drawResizeGrip(graphics, getPositionX() + getSizeWidth(), getPositionY() + getSizeHeight(), resizing || isOverGrip(mouseX, mouseY), isLocked());
        pose.popPose();
    }

    /** 悬停在缩放角上时说明操作：没锁定时"拖动调整 / 右键锁定"，锁定时"已锁定 / 右键解锁"。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean inside = isInViewport(mouseX, mouseY);
        int x = getPositionX(), y = getPositionY();
        var pose = graphics.pose().last().pose();
        var from = pose.transform(new Vector4f(x, y, 0, 1));
        var to = pose.transform(new Vector4f(x + getSizeWidth(), y + getSizeHeight(), 0, 1));
        graphics.enableScissor((int) from.x, (int) from.y, (int) to.x, (int) to.y);
        drawWidgetsForeground(graphics, inside ? mouseX : OUTSIDE, inside ? mouseY : OUTSIDE, partialTicks);
        graphics.disableScissor();
        if (resizing || !isOverGrip(mouseX, mouseY) || gui == null || gui.getModularUIGui() == null) return;
        var lines = isLocked() ?
                List.<Component>of(Component.translatable(GRIP_LOCKED), Component.translatable(GRIP_UNLOCK).withStyle(ChatFormatting.GRAY)) :
                List.<Component>of(Component.translatable(GRIP_RESIZE), Component.translatable(GRIP_LOCK).withStyle(ChatFormatting.GRAY));
        gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
    }
}
