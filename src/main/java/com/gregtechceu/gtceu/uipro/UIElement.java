package com.gregtechceu.gtceu.uipro;

import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.data.UIFrames;
import com.gregtechceu.gtceu.uipro.render.UIClip;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AvailableSpace;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.tree.NodeId;
import dev.vfyjxf.taffy.tree.TaffyTree;
import dev.vfyjxf.taffy.util.MeasureFunc;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 在 LDLib1 上仿照 LDLib2 {@code UIElement} 的通用节点：任何元素都能挂子元素、声明布局、挂同步值。
 * <p>
 * <b>布局</b>与 LDLib2 一样由 Taffy（taffy-java）计算，语义是 CSS Flexbox（见 {@link LayoutStyle}）：
 * <ul>
 * <li>一棵 UIElement 树（从父控件不是 UIElement 的那个元素算起）共用一个 {@link TaffyTree}，每个 UIElement 一个节点；</li>
 * <li>树里的 LDLib1 原生控件是叶子：默认固定为控件当前尺寸，实现 {@link ILayoutItem} 的按自己的样式 / 测量函数；</li>
 * <li>增删子元素、改样式、子控件尺寸变化都会标脏，随即同步计算，把结果（位置、尺寸）写回 LDLib1 控件；</li>
 * <li>树根尺寸变了会通知外壳（{@link ILayoutHost}），窗口随内容变化；树根放在其他 LDLib1 容器里（如滚动区的内容）时，
 * 照常经 LDLib1 的 {@code onChildSizeUpdate} 通知该容器。</li>
 * </ul>
 * 其他对应关系：{@link #addChild}/{@link #addChildren} ↔ {@code addChild/addChildren}；{@link #setDisplay} ↔ {@code setDisplay}
 * （隐藏且不占位置）；{@link #addSyncValue} ↔ {@code addSyncValue}，但同步范围是本元素，而非整棵 UI。
 * <p>
 * 布局两端都算（控件树两端一致，尺寸可以不同：文字只在客户端测量），服务端的尺寸不影响同步。
 */
public class UIElement extends WidgetGroup implements ElementState.Host<UIElement>, UIChannel.Host {

    private static final Logger LOGGER = LogUtils.getLogger();
    /// 一次布局里"算完、写回后又被标脏"最多重算的次数，超过说明样式在互相震荡
    private static final int MAX_LAYOUT_PASSES = 10;

    protected final LayoutStyle style = new LayoutStyle(this::markLayoutDirty);
    private final UIChannel channel = new UIChannel(this);
    private final ElementState state = new ElementState(this, this);
    public static final int OUTSIDE = -100000;

    @Nullable
    private ViewTransform transform;
    private boolean clipChildren;
    private boolean closeListener;
    private boolean closeHooked;

    /// 本元素作为树根时持有的布局树；不是根时为 null
    @Nullable
    private LayoutTree layoutTree;
    /// 本元素在所属布局树里的节点，nodeTree 是节点所属的树（结构重建时重新分配）
    @Nullable
    private NodeId node;
    @Nullable
    private LayoutTree nodeTree;
    private boolean layoutDeferred;
    private int addingChild;
    private int updatingPosition;
    private int styleBatch;
    private boolean styleBatchDirty;

    public UIElement() {
        super(Position.ORIGIN, Size.ZERO);
    }

    /** 纵向容器；{@code width} 为 {@link LayoutStyle#AUTO} 时宽度由内容 / 父元素决定。高度随内容。 */
    public static UIElement column(int width) {
        return new UIElement().layout(l -> l.column().width(width));
    }

    /** 横向容器，固定高度；宽度随内容，或被父元素拉伸。 */
    public static UIElement row(int height) {
        return new UIElement().layout(l -> l.row().height(height));
    }

    public static UIElement centeredRow(int height) {
        return row(height).layout(l -> l.gapAll(UISizes.GAP).alignCenter());
    }

    /** 占位空白，对应 LDLib2 里一个只设了尺寸的空元素。 */
    public static Widget spacer(int width, int height) {
        return new Widget(0, 0, width, height);
    }

    /**
     * 带面板底图（{@link UITheme#PANEL}，浅灰凸起，里面的字用 {@link UITheme#PANEL_TEXT}）和内边距的纵向区块，用来把一组相关控件框在一起。
     * {@code width} 为 {@link LayoutStyle#AUTO} 时宽度由内容 / 父元素决定。
     */
    public static UIElement section(int width) {
        var section = new UIElement().layout(l -> l.column().width(width).gapAll(UISizes.GAP)
                .paddingAll(UISizes.PANEL_PADDING).paddingBottom(UISizes.PANEL_PADDING_BOTTOM));
        section.setBackground(UITheme.PANEL);
        return section;
    }

    /** 宽度由内容 / 父元素决定的区块。 */
    public static UIElement section() {
        return section(LayoutStyle.AUTO);
    }

    /** 横向行里吃掉剩余宽度的空白，用来把后面的元素推到右侧。 */
    public static UIElement flexSpacer() {
        return new UIElement().layout(l -> l.flexGrow(1).size(0, 0));
    }

    public UIElement layout(Consumer<LayoutStyle> layout) {
        styleBatch++;
        Throwable failure = null;
        try {
            layout.accept(style);
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            if (--styleBatch == 0 && styleBatchDirty) {
                styleBatchDirty = false;
                if (failure == null) {
                    markLayoutDirty();
                } else {
                    try {
                        markLayoutDirty();
                    } catch (Throwable t) {
                        failure.addSuppressed(t);
                    }
                }
            }
        }
        return this;
    }

    public LayoutStyle getLayoutStyle() {
        return style;
    }

    public UIElement addChild(Widget child) {
        addWidget(child);
        return this;
    }

    public UIElement addChildren(Widget... children) {
        for (var child : children) addWidget(child);
        return this;
    }

    @Override
    public <T> SyncValue<T> addSyncValue(SyncValue<T> value) {
        return channel.addSyncValue(value);
    }

    @Override
    public UIChannel getChannel() {
        return channel;
    }

    /**
     * 显示 / 隐藏（对应 LDLib2 {@code setDisplay}）：隐藏的元素不绘制、不响应操作，也不占布局位置（{@code display: none}）。
     * 只改本端，界面结构（控件树）不变；两端需要一致时由调用方在两端各调一次。
     */
    public UIElement setDisplay(boolean display) {
        setVisible(display);
        setActive(display);
        if (style.isDisplayed() != display) style.display(display ? TaffyDisplay.FLEX : TaffyDisplay.NONE);
        return this;
    }

    public boolean isDisplayed() {
        return style.isDisplayed();
    }

    // ==================== 交互状态（LDLib2 概念，见 ElementState） ====================

    @Override
    public ElementState getState() {
        return state;
    }

    /**
     * 选中（LDLib2 {@code setSelected}）：条件为真时画统一选中框（{@link UIDraw#selectionFrame}），物品槽、列表项等都用它。
     * 条件在客户端每帧判定，只应依赖本端界面状态（如所在窗口的弹出面板是否打开）——选中是"这个界面"的状态，
     * 放在机器字段上多人同时打开时会互相干扰。
     */
    @Override
    public UIElement setSelected(@Nullable BooleanSupplier selected) {
        return ElementState.Host.super.setSelected(selected);
    }

    /**
     * 按服务端条件禁用（LDLib2 {@code disabled()}）：本元素下所有可操作的控件都禁用、叠统一斜纹，
     * 悬停提示先"禁止操作"再原因 {@code reasonKey}（翻译键，可为 null）。整行、整个区块设一次即可。建界面时两端都要调用。
     */
    @Override
    public UIElement disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        return ElementState.Host.super.disabled(serverCondition, reasonKey);
    }

    /** 选中框画在前景层：所有元素的背景（含之后才绘制的相邻槽）都画完后再画，外扩的那 1 像素不会被邻格盖住。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (transform == null) {
            if (isSelected()) UIDraw.selectionFrame(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        transform.apply(pose, getPositionX(), getPositionY());
        if (isSelected()) UIDraw.selectionFrame(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        super.drawInForeground(graphics, (int) Math.floor(toLocalX(mouseX)), (int) Math.floor(toLocalY(mouseY)), partialTicks);
        pose.popPose();
    }

    /**
     * 建界面时可用的内容宽度：声明了宽度（或最小宽度）时为它减去左右内边距，否则 {@link Integer#MAX_VALUE}。
     * 只用于尺寸本来就固定的界面（如一行 9 个槽）；会随父元素变化的，用拉伸 / flexGrow，不要按它写死。
     */
    public int getContentWidth() {
        int width = style.getDeclaredWidth();
        if (width == LayoutStyle.AUTO) width = style.getDeclaredMinWidth() > 0 ? style.getDeclaredMinWidth() : LayoutStyle.AUTO;
        return width == LayoutStyle.AUTO ? Integer.MAX_VALUE : width - style.horizontalPadding();
    }

    /** 声明了高度时为它减去上下内边距，否则 {@link Integer#MAX_VALUE}。 */
    public int getContentHeight() {
        int height = style.getDeclaredHeight();
        return height == LayoutStyle.AUTO ? Integer.MAX_VALUE : height - style.verticalPadding();
    }

    // ==================== 布局（Taffy） ====================

    /** 一棵 UIElement 树的布局状态，由树根持有。 */
    private static final class LayoutTree {

        final TaffyTree taffy = new TaffyTree();
        /// 非 UIElement 叶子控件 → 节点
        final Map<Widget, NodeId> leaves = new IdentityHashMap<>();
        boolean structureDirty = true;
        boolean running;
        boolean pending;
        /// 正在由布局写回尺寸的叶子控件：它引起的 onChildSizeUpdate 是布局自己造成的，忽略
        @Nullable
        Widget applying;
    }

    /**
     * 叶子测量函数（对应 Taffy {@link MeasureFunc}）：没有子控件、尺寸由内容决定的元素（如自动换行的文字）重写它。
     * 内容变化后调用 {@link #markLayoutDirty()}。构造期间也可能被调用，实现要能处理字段尚未初始化的情况。
     */
    @Nullable
    protected MeasureFunc getMeasure() {
        return null;
    }

    /** 样式或测量结果变了：标脏并重新布局。 */
    public void markLayoutDirty() {
        if (styleBatch > 0) {
            styleBatchDirty = true;
            return;
        }
        var root = layoutRoot();
        var tree = root.layoutTree;
        if (tree != null) {
            if (!tree.structureDirty && node != null && nodeTree == tree) tree.taffy.markDirty(node);
            else tree.structureDirty = true;
        }
        root.requestLayout();
    }

    /** 非 UIElement 的 {@link ILayoutItem} 控件样式或测量结果变了：通知所在的 UIElement 重新布局。 */
    public static void markLayoutDirty(Widget widget) {
        if (widget instanceof UIElement element) {
            element.markLayoutDirty();
        } else if (widget.getParent() instanceof UIElement parent && parent.widgets.contains(widget)) {
            parent.markLeafDirty(widget);
        }
    }

    private void markLeafDirty(Widget leaf) {
        var root = layoutRoot();
        var tree = root.layoutTree;
        if (tree != null) {
            var leafNode = tree.leaves.get(leaf);
            if (!tree.structureDirty && leafNode != null) tree.taffy.markDirty(leafNode);
            else tree.structureDirty = true;
        }
        root.requestLayout();
    }

    private void markStructureDirty() {
        var root = layoutRoot();
        if (root.layoutTree != null) root.layoutTree.structureDirty = true;
        root.requestLayout();
    }

    private boolean isLayoutRoot() {
        return !(getParent() instanceof UIElement parent) || !parent.widgets.contains(this);
    }

    private UIElement layoutRoot() {
        var element = this;
        while (!element.isLayoutRoot()) element = (UIElement) element.getParent();
        return element;
    }

    private LayoutTree tree() {
        if (layoutTree == null) layoutTree = new LayoutTree();
        return layoutTree;
    }

    /** 本元素是树根：算到不再变脏为止（写回尺寸可能引发新的样式变化，如滚动条显隐）。 */
    private void requestLayout() {
        if (layoutTree != null && layoutTree.running) {
            layoutTree.pending = true;
            return;
        }
        if (getParent() == null) {
            layoutDeferred = true;
            return;
        }
        layoutDeferred = false;
        layoutNow();
    }

    private void flushDeferredLayout() {
        for (int pass = 0; layoutDeferred; pass++) {
            if (layoutTree != null && layoutTree.running) {
                layoutTree.pending = true;
                return;
            }
            if (pass >= MAX_LAYOUT_PASSES) {
                LOGGER.warn("UI layout still deferred after {} passes, root {}", MAX_LAYOUT_PASSES, getClass().getName());
                return;
            }
            layoutDeferred = false;
            layoutNow();
        }
    }

    private void layoutNow() {
        var tree = tree();
        tree.running = true;
        boolean applied;
        try {
            applied = runLayoutPasses(tree);
        } finally {
            tree.running = false;
        }
        if (applied) onLayoutFinished();
    }

    /** 重建（如需）、计算、写回，直到不再变脏；返回是否写回过。 */
    private boolean runLayoutPasses(LayoutTree tree) {
        boolean applied = false;
        for (int pass = 0;; pass++) {
            tree.pending = false;
            if (tree.structureDirty) rebuild(tree);
            if (node == null) return applied;
            if (!tree.taffy.isDirty(node)) {
                if (!tree.pending) return applied;
                continue;
            }
            if (pass >= MAX_LAYOUT_PASSES) {
                LOGGER.warn("UI layout still dirty after {} passes, root {}", MAX_LAYOUT_PASSES, getClass().getName());
                return applied;
            }
            // 全量重算：taffy-java 的缓存会在父节点命中最终布局缓存时不再下发子节点布局，子节点留着测量阶段的结果
            // （如行里 flex 的输入框被留成 0 宽）。界面树很小，清掉整棵树的缓存代价可以忽略
            for (var id : tree.taffy.getAllNodes()) tree.taffy.clearCache(id);
            tree.taffy.computeLayout(node, new TaffySize<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT));
            applyLayout(tree, true);
            applied = true;
        }
    }

    /**
     * 树根：一次布局（可能多轮）全部写回之后调用。放在其他 LDLib1 容器里的树根（如滚动区的内容）用它通知容器，
     * 此时布局已经稳定，容器可以放心再触发别的布局。
     */
    protected void onLayoutFinished() {}

    /** 按控件树重建整棵 Taffy 树（增删子元素后）。样式对象按引用共享，重建不丢样式。 */
    private void rebuild(LayoutTree tree) {
        tree.structureDirty = false;
        tree.taffy.clear();
        tree.leaves.clear();
        buildNode(tree);
    }

    private NodeId buildNode(LayoutTree tree) {
        if (tree != layoutTree) {
            layoutTree = null;
            layoutDeferred = false;
        }
        var measure = widgets.isEmpty() ? getMeasure() : null;
        var id = measure == null ? tree.taffy.newLeaf(style.style) : tree.taffy.newLeafWithMeasure(style.style, measure);
        node = id;
        nodeTree = tree;
        var children = new NodeId[widgets.size()];
        for (int i = 0; i < children.length; i++) {
            var widget = widgets.get(i);
            children[i] = widget instanceof UIElement element ? element.buildNode(tree) : leafNode(tree, widget);
        }
        if (children.length > 0) tree.taffy.setChildren(id, children);
        return id;
    }

    private static NodeId leafNode(LayoutTree tree, Widget widget) {
        NodeId id;
        if (widget instanceof ILayoutItem item) {
            var measure = item.getLayoutMeasure();
            var itemStyle = item.getLayoutStyle().style;
            id = measure == null ? tree.taffy.newLeaf(itemStyle) : tree.taffy.newLeafWithMeasure(itemStyle, measure);
        } else {
            id = tree.taffy.newLeaf(LayoutStyle.fixed(widget.getSizeWidth(), widget.getSizeHeight(), () -> {}).style);
        }
        tree.leaves.put(widget, id);
        return id;
    }

    /** 把 Taffy 的结果写回 LDLib1 控件：自身尺寸，子控件位置与尺寸。树根的位置由它所在的容器决定，不动。 */
    private void applyLayout(LayoutTree tree, boolean root) {
        if (node == null) return;
        var layout = tree.taffy.getLayout(node);
        setSize(new Size(Math.round(layout.size().width), Math.round(layout.size().height)));
        for (var widget : widgets) {
            if (widget instanceof UIElement element) {
                if (!element.isDisplayed() || element.node == null) continue;
                var child = tree.taffy.getLayout(element.node);
                element.setSelfPosition(new Position(Math.round(child.location().x), Math.round(child.location().y)));
                element.applyLayout(tree, false);
            } else {
                var leaf = tree.leaves.get(widget);
                if (leaf == null) continue;
                var child = tree.taffy.getLayout(leaf);
                widget.setSelfPosition(new Position(Math.round(child.location().x), Math.round(child.location().y)));
                tree.applying = widget;
                try {
                    widget.setSize(new Size(Math.round(child.size().width), Math.round(child.size().height)));
                } finally {
                    tree.applying = null;
                }
            }
        }
    }

    /// LDLib1 在增删子控件时调用：结构变了
    @Override
    protected void recomputeLayout() {
        markStructureDirty();
    }

    /// 子控件位置一律由布局决定，不因子控件自己挪动而重排
    @Override
    protected void onChildSelfPositionUpdate(Widget child) {}

    /// UIElement 子元素的尺寸由布局决定；原生控件的尺寸变了（不是布局写回造成的）要重新布局
    @Override
    protected void onChildSizeUpdate(Widget child) {
        if (child instanceof UIElement) return;
        var tree = layoutRoot().layoutTree;
        if (tree != null && tree.applying == child) return;
        if (child instanceof ILayoutItem) markLeafDirty(child);
        else markStructureDirty();
    }

    @Override
    public Size getSize() {
        if (gui == null) layoutRoot().flushDeferredLayout();
        return super.getSize();
    }

    @Override
    public Position getPosition() {
        if (gui == null && addingChild == 0 && updatingPosition == 0) layoutRoot().flushDeferredLayout();
        return super.getPosition();
    }

    @Override
    protected void onPositionUpdate() {
        updatingPosition++;
        try {
            super.onPositionUpdate();
        } finally {
            updatingPosition--;
        }
    }

    @Override
    public WidgetGroup addWidget(int index, Widget widget) {
        addingChild++;
        try {
            var result = super.addWidget(index, widget);
            channel.onChildAdded(widget);
            return result;
        } finally {
            addingChild--;
        }
    }

    @Override
    public void initWidget() {
        super.initWidget();
        channel.prime();
    }

    @Override
    protected void setParent(WidgetGroup parent) {
        if (parent != null && !(parent instanceof UIElement)) flushDeferredLayout();
        super.setParent(parent);
    }

    /** 树根（父控件不是 UIElement）尺寸变了，通知最近的 {@link ILayoutHost}；树内元素的尺寸由布局统一写回。 */
    @Override
    protected void onSizeUpdate() {
        super.onSizeUpdate();
        if (!isLayoutRoot()) return;
        for (var ancestor = getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof ILayoutHost host) {
                host.onContentResized(this);
                return;
            }
        }
    }

    public UIElement setTransform(@Nullable ViewTransform transform) {
        this.transform = transform;
        return this;
    }

    @Nullable
    public ViewTransform getTransform() {
        return transform;
    }

    public UIElement setClipChildren(boolean clipChildren) {
        this.clipChildren = clipChildren;
        return this;
    }

    public boolean isClipChildren() {
        return clipChildren;
    }

    public int getClipX() {
        return getPositionX();
    }

    public int getClipY() {
        return getPositionY();
    }

    public int getClipWidth() {
        return getSizeWidth();
    }

    public int getClipHeight() {
        return getSizeHeight();
    }

    public boolean isInClip(double localX, double localY) {
        return !clipChildren || isMouseOver(getClipX(), getClipY(), getClipWidth(), getClipHeight(), localX, localY);
    }

    public double toLocalX(double x) {
        return transform == null ? x : transform.toLocalX(x, getPositionX());
    }

    public double toLocalY(double y) {
        return transform == null ? y : transform.toLocalY(y, getPositionY());
    }

    private double localScale() {
        return transform == null ? 1 : transform.getScale();
    }

    public boolean isPointerOver(double mouseX, double mouseY) {
        if (!isVisible() || !isMouseOverElement(mouseX, mouseY)) return false;
        Widget current = this;
        double x = mouseX, y = mouseY;
        while (current.getParent() != null) {
            var parent = current.getParent();
            int index = parent.widgets.indexOf(current);
            for (int i = index + 1; i < parent.widgets.size(); i++) {
                var sibling = parent.widgets.get(i);
                if (sibling.isVisible() && sibling.isActive() && sibling.isMouseOverElement(x, y)) return false;
            }
            if (parent instanceof UIElement element) {
                if (!element.isInClip(x, y)) return false;
                if (element.transform != null) {
                    x = element.transform.toParentX(x, element.getPositionX());
                    y = element.transform.toParentY(y, element.getPositionY());
                }
            }
            if (parent instanceof ILayoutHost) break;
            current = parent;
        }
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean isTextInputFocused() {
        return gui != null && gui.getModularUIGui() != null && gui.getModularUIGui().lastFocus instanceof TextFieldWidget;
    }

    @OnlyIn(Dist.CLIENT)
    public void capturePointer(int button) {
        UIInput.capture(this, button);
    }

    @OnlyIn(Dist.CLIENT)
    public void releasePointer() {
        UIInput.release(this);
    }

    protected final void listenUIClose() {
        closeListener = true;
        if (gui != null) hookClose();
    }

    protected void onUIClosed() {}

    private void hookClose() {
        if (closeHooked || gui == null) return;
        closeHooked = true;
        gui.registerCloseListener(this::onUIClosed);
    }

    @Override
    public void setGui(ModularUI gui) {
        super.setGui(gui);
        if (closeListener && gui != null) hookClose();
    }

    @Nullable
    private Widget childToward(Widget target) {
        for (Widget current = target; current != null; current = current.getParent()) {
            if (current.getParent() == this) return current;
        }
        return null;
    }

    @Override
    public boolean isMouseOverElement(double mouseX, double mouseY) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY);
        if (!clipChildren) return super.isMouseOverElement(x, y);
        return isMouseOver(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), x, y);
    }

    @Override
    public @Nullable Widget getHoverElement(double mouseX, double mouseY) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY);
        if (!isInClip(x, y)) return isMouseOver(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), x, y) ? this : null;
        return super.getHoverElement(x, y);
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY);
        if (!isInClip(x, y)) return null;
        return super.getXEIIngredientOverMouse(x, y);
    }

    @Override
    public List<Target> getPhantomTargets(Object ingredient) {
        var targets = super.getPhantomTargets(ingredient);
        if (transform == null && !clipChildren || targets.isEmpty()) return targets;
        return mapTargets(targets);
    }

    @OnlyIn(Dist.CLIENT)
    private List<Target> mapTargets(List<Target> targets) {
        var mapped = new ArrayList<Target>(targets.size());
        for (var target : targets) {
            var area = transform == null ? target.getArea() : transform.toParent(target.getArea(), getPositionX(), getPositionY());
            if (clipChildren && !intersectsClip(area)) continue;
            mapped.add(new Target() {

                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(Object value) {
                    target.accept(value);
                }
            });
        }
        return mapped.isEmpty() ? Collections.emptyList() : mapped;
    }

    @OnlyIn(Dist.CLIENT)
    private boolean intersectsClip(Rect2i area) {
        double x0 = getClipX(), y0 = getClipY(), x1 = x0 + getClipWidth(), y1 = y0 + getClipHeight();
        if (transform != null) {
            x0 = transform.toParentX(x0, getPositionX());
            y0 = transform.toParentY(y0, getPositionY());
            x1 = transform.toParentX(x1, getPositionX());
            y1 = transform.toParentY(y1, getPositionY());
        }
        return area.getX() < x1 && area.getX() + area.getWidth() > x0 && area.getY() < y1 && area.getY() + area.getHeight() > y0;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY);
        if (!isInClip(x, y)) return false;
        return super.mouseClicked(x, y, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY), s = localScale();
        var captured = UIInput.captured();
        if (captured != null && captured != this) {
            var child = childToward(captured);
            if (child == null) return false;
            child.mouseDragged(x, y, button, dragX / s, dragY / s);
            return true;
        }
        return super.mouseDragged(x, y, button, dragX / s, dragY / s);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY);
        var captured = UIInput.captured();
        if (captured != null && captured != this && button == UIInput.capturedButton()) {
            var child = childToward(captured);
            if (child == null) return false;
            child.mouseReleased(x, y, button);
            UIInput.release(captured);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY);
        if (!isInClip(x, y)) return false;
        return super.mouseWheelMove(x, y, wheelDelta);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseMoved(double mouseX, double mouseY) {
        double x = toLocalX(mouseX), y = toLocalY(mouseY);
        if (!isInClip(x, y)) return false;
        return super.mouseMoved(x, y);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (transform == null) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        transform.apply(pose, getPositionX(), getPositionY());
        super.drawInBackground(graphics, (int) Math.floor(toLocalX(mouseX)), (int) Math.floor(toLocalY(mouseY)), partialTicks);
        pose.popPose();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawWidgetsBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!clipChildren) {
            super.drawWidgetsBackground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        pushClip(graphics);
        boolean inside = isInClip(mouseX, mouseY);
        super.drawWidgetsBackground(graphics, inside ? mouseX : OUTSIDE, inside ? mouseY : OUTSIDE, partialTicks);
        UIClip.pop(graphics);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawWidgetsForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!clipChildren) {
            super.drawWidgetsForeground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        pushClip(graphics);
        boolean inside = isInClip(mouseX, mouseY);
        super.drawWidgetsForeground(graphics, inside ? mouseX : OUTSIDE, inside ? mouseY : OUTSIDE, partialTicks);
        UIClip.pop(graphics);
    }

    @OnlyIn(Dist.CLIENT)
    public void pushClip(GuiGraphics graphics) {
        UIClip.push(graphics, getClipX(), getClipY(), getClipWidth(), getClipHeight());
    }

    // ==================== 同步 ====================

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        channel.writeHead(buffer);
        for (var widget : widgets) {
            if (widget.isInitialized() && !widget.isClientSideWidget()) UIFrames.write(buffer, widget::writeInitialData);
        }
        channel.writeTail(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        channel.readHead(buffer);
        for (var widget : widgets) {
            if (widget.isInitialized() && !widget.isClientSideWidget()) UIFrames.read(buffer, widget::readInitialData, widget);
        }
        channel.readTail(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        channel.detectAndSendChanges();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!channel.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (!channel.handleClientAction(id, buffer)) super.handleClientAction(id, buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        channel.pollClient();
    }
}
