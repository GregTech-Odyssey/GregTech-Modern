package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.ILayoutHost;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.animation.Animation;
import com.gregtechceu.gtceu.uipro.animation.AnimationEngine;
import com.gregtechceu.gtceu.uipro.animation.Eases;
import com.gregtechceu.gtceu.uipro.animation.PixelSnap;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.LockedScrollerSizes;
import com.gregtechceu.gtceu.uipro.window.CardHost;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 画布：可平移、缩放的无限平面，对应 LDLib2 {@code GraphView}。科技树、节点图这类"比窗口大得多、要拖着看"的内容用它。
 * <ul>
 * <li><b>视图</b>：{@link #offsetX()}/{@link #offsetY()} 是视口左上角的世界坐标，{@link #scale()} 是缩放（1 个世界单位占几个界面像素），
 * 夹在 {@link #setScaleRange} 之间。平移范围按内容实时算：最远可以拖到最边上的内容刚好移出视口。</li>
 * <li><b>操作</b>：左键或中键拖动平移（按下后移动超过 3 像素才算拖动，否则是点击）；滚轮以鼠标为中心缩放；
 * 点击项时回调 {@link #setOnItemClick}，点在空白处回调 {@link #setOnBackgroundClick}。悬停项显示提示、交给 EMI（查配方 / 用途）。</li>
 * <li><b>内容</b>：若干 {@link CanvasLayer}，从下往上画、从上往下命中；只画与视口相交的项；
 * 按物理像素比例切换细节层级（{@link CanvasLod}，同 LDLib2）。背景是自适应密度的网格（{@link CanvasGrid}，同 LDLib2）。</li>
 * <li><b>悬浮层</b>：画布的子元素（通常是一个 {@link com.gregtechceu.gtceu.uipro.elements.Dock}，控制按钮见 {@link CanvasControls}）
 * 浮在内容之上、底部居中，点在它上面不会落到画布。另有可开关的缩略图（右上角，点击 / 拖动跳转）。
 * 可再放一张浮在右侧的卡片（{@link #addFloatingCard}，如选中项的详情）：打开时定位、适应只用它左边露出的部分，缩略图让到它左边。</li>
 * <li><b>尺寸</b>：默认取构造时的首选宽高。右下角缩放角两个方向都能拖（画布两个方向都能平移），不保存；
 * 能拖多大由外壳决定（一般界面不超过屏幕 2/3）；右键锁定当前尺寸（按 {@link #id} 存在客户端本地，重开界面沿用）。规则同 {@code ScrollerView}。</li>
 * <li><b>动画</b>：适应全部、定位、按钮缩放用 0.25 秒缓动过渡（{@link AnimationEngine}）；滚轮缩放立即生效。</li>
 * </ul>
 * 画布是纯客户端的展示：视图状态、图层都只在客户端，两端控件树只有画布本身和它的悬浮层，与内容多少无关。
 * 图层在 {@link #setScene} 的回调里建（只在客户端执行）；要改服务端数据的交互由使用方自己发请求。
 */
public class CanvasView extends UIElement {

    public static final String GRIP_RESIZE = "gtceu.uipro.canvas.resize";
    public static final String GRIP_LOCK = "gtceu.uipro.canvas.lock";
    public static final String GRIP_LOCKED = "gtceu.uipro.canvas.locked";
    public static final String GRIP_UNLOCK = "gtceu.uipro.canvas.unlock";
    /** 画布操作说明：拖动平移、滚轮缩放（使用方可放进 {@code InfoIcon}）。 */
    public static final String HELP_PAN = "gtceu.uipro.canvas.help.pan";
    public static final String HELP_ZOOM = "gtceu.uipro.canvas.help.zoom";

    /// 视口相对外框的内缩（1 像素斜面边）
    private static final int FRAME = 1;
    /// 按下后移动超过这么多界面像素才算拖动
    private static final double DRAG_THRESHOLD = 3;
    private static final float WHEEL_ZOOM_STEP = 1.15f;
    /** 按钮缩放一次的倍数。 */
    public static final float BUTTON_ZOOM_STEP = 1.5f;
    private static final int MIN_SIZE = UISizes.CANVAS_MIN_SIZE;
    private static final Animation VIEW_ANIMATION = Animation.of(0.25f, Eases.CUBIC_OUT);
    /// 悬浮层、缩略图、缩放角的绘制高度：高于物品图标（约 150~160）与选中框（300）
    private static final int OVERLAY_Z = 310;
    /// 网格每级最多画的线数：配置出错时也不至于每帧画上千条线
    private static final int MAX_GRID_LINES = 512;
    /// 不在任何子控件上的鼠标坐标
    private static final int OUTSIDE = -100000;

    /** 固定 id（按界面位置取，如 {@code techtree.canvas}），锁定的尺寸按它保存。 */
    private final String id;
    private final List<CanvasLayer> layers = new ArrayList<>();
    private int preferredWidth, preferredHeight;
    /// 本次打开界面拖出来的尺寸（不保存），-1 为没拖过
    private int userWidth = -1, userHeight = -1;
    /// 锁定的尺寸（客户端本地保存），-1 为没锁定
    private int lockedWidth = -1, lockedHeight = -1;
    private boolean resizable = true;
    /// 按屏幕撑大默认尺寸：{其余部分占的宽, 其余部分占的高, 最大宽, 最大高}，null 为不撑
    @Nullable
    private int[] screenFill;

    private float minScale = 0.1f, maxScale = 4f;
    private float lodSimplifiedPixelScale = 0.5f, lodBlockPixelScale = 0.2f;
    private boolean allowPan = true, allowZoom = true;
    @Nullable
    private CanvasGrid grid = new CanvasGrid(UISizes.CANVAS_GRID_SIZE, UISizes.CANVAS_GRID_MIN_PIXELS, 4,
            UITheme.CANVAS_GRID_LINE, UITheme.CANVAS_GRID_ACCENT);

    // ==================== 视图（客户端） ====================
    private float offsetX, offsetY, scale = 1;
    @Nullable
    private Consumer<CanvasView> scene;
    private boolean sceneBuilt;
    @Nullable
    private Consumer<CanvasView> initialView;
    private boolean viewInitialized;
    private int lastViewportWidth = -1, lastViewportHeight = -1;
    private final AnimationEngine animations = new AnimationEngine();
    @Nullable
    private AnimationEngine.Playback viewAnimation;
    private boolean minimapShown;
    /// 右侧悬浮卡片位（两端都有，是控件树的一部分），没有为 null
    @Nullable
    private CardHost floatingCard;
    /// 内容范围缓存：图层增删时失效（图层内容要在 addLayer 之前建好，之后改了调 invalidateContentBounds）
    private boolean contentBoundsValid;
    @Nullable
    private CanvasRect contentBounds;
    /// 缩略图在屏幕上的范围 {x, y, w, h}（每帧绘制时算一次，悬停、点击沿用）；没有缩略图时 w = 0
    private final int[] minimap = new int[4];
    /// 每帧复用的绘制对象（画笔是纯客户端类，第一次绘制时才创建）
    @Nullable
    private CanvasPainter painter;
    private final List<CanvasRect> selectedRects = new ArrayList<>(1);
    private final Vector4f scissorMin = new Vector4f(), scissorMax = new Vector4f();

    // ==================== 交互（客户端） ====================
    @Nullable
    private CanvasItem hovered;
    private int pressButton = -1;
    /// 按下时的鼠标位置与视图偏移：平移按"按下时的偏移 + 鼠标总位移"重算（同 LDLib2），不逐帧累加，始终跟手
    private double pressX, pressY;
    private float pressOffsetX, pressOffsetY;
    private boolean panning;
    @Nullable
    private CanvasItem pressedItem;
    private boolean minimapDragging;
    private boolean resizing;
    private double resizeStartX, resizeStartY;
    private int resizeStartWidth, resizeStartHeight;
    private int resizeMaxWidth = Integer.MAX_VALUE, resizeMaxHeight = Integer.MAX_VALUE;
    @Nullable
    private ItemClickListener onItemClick;
    @Nullable
    private BackgroundClickListener onBackgroundClick;

    @FunctionalInterface
    public interface ItemClickListener {

        /** 点击了画布里的项（客户端）；{@code button} 为鼠标键。 */
        void onClick(CanvasItem item, int button);
    }

    @FunctionalInterface
    public interface BackgroundClickListener {

        /** 点在空白处（没有拖动）；{@code worldX/Y} 为世界坐标。 */
        void onClick(float worldX, float worldY, int button);
    }

    /**
     * @param id     固定 id（按界面里的位置取），锁定的尺寸按它保存
     * @param width  首选宽度
     * @param height 首选高度
     */
    public CanvasView(String id, int width, int height) {
        this.id = id;
        this.preferredWidth = width;
        this.preferredHeight = height;
        // 悬浮层（子元素）在底部居中，离底边 DOCK_MARGIN
        layout(l -> l.column().justifyContent(AlignContent.FLEX_END).alignCenter().paddingAll(UISizes.DOCK_MARGIN));
        applySize();
    }

    // ==================== 配置 ====================

    /** 内容（图层）：回调只在客户端执行（界面初始化时一次，{@link #rebuildScene()} 时再执行），在里面 {@link #addLayer}。 */
    public CanvasView setScene(Consumer<CanvasView> scene) {
        this.scene = scene;
        if (isInitialized() && isRemote()) rebuildScene();
        return this;
    }

    /** 清空图层、重新执行内容回调（客户端，如切换了要显示的内容）。视图位置不变，需要时再调 {@link #fitContent} 等。 */
    public void rebuildScene() {
        layers.clear();
        contentBoundsValid = false;
        hovered = null;
        pressedItem = null;
        sceneBuilt = true;
        if (scene != null) scene.accept(this);
    }

    /** 加一层（放在已有图层之上）。图层的内容要先建好；加入后内容范围变了调 {@link #invalidateContentBounds()}。 */
    public CanvasView addLayer(CanvasLayer layer) {
        layers.add(layer);
        contentBoundsValid = false;
        return this;
    }

    /** 图层内容范围变了（加入后又增删了项）：让"适应全部"、平移范围、缩略图重新取范围。 */
    public void invalidateContentBounds() {
        contentBoundsValid = false;
    }

    /**
     * 第一次拿到视口尺寸时怎样摆放视图（客户端，只执行一次），例如 {@link #showStart}、{@link #fitContent} 或定位到某一项。
     * 默认 {@link #showStart}。
     */
    public CanvasView setInitialView(Consumer<CanvasView> initialView) {
        this.initialView = initialView;
        return this;
    }

    /** 第一帧之后（视口尺寸已知、初始视图已摆好）。 */
    public boolean isViewInitialized() {
        return viewInitialized;
    }

    /**
     * 视图操作要等视口尺寸已知才有意义（{@link #focus}、{@link #fit} 都按视口尺寸算）：第一帧之前调用时，
     * 改为替换初始视图（第一帧再执行、不播动画）；之后立即执行。界面打开时就要定位到某一项（如同步数据一到就定位）用它。
     */
    public void whenReady(Consumer<CanvasView> action) {
        if (viewInitialized) action.accept(this);
        else initialView = action;
    }

    public CanvasView setScaleRange(float minScale, float maxScale) {
        this.minScale = minScale;
        this.maxScale = Math.max(minScale, maxScale);
        this.scale = Mth.clamp(scale, this.minScale, this.maxScale);
        return this;
    }

    /** 细节层级阈值（物理像素 / 世界单位），见 {@link CanvasLod#resolve}。 */
    public CanvasView setLodThresholds(float simplifiedPixelScale, float blockPixelScale) {
        this.lodSimplifiedPixelScale = simplifiedPixelScale;
        this.lodBlockPixelScale = blockPixelScale;
        return this;
    }

    public CanvasView setAllowPan(boolean allowPan) {
        this.allowPan = allowPan;
        return this;
    }

    public CanvasView setAllowZoom(boolean allowZoom) {
        this.allowZoom = allowZoom;
        return this;
    }

    /** 背景网格，null 为不画。 */
    public CanvasView setGrid(@Nullable CanvasGrid grid) {
        this.grid = grid;
        return this;
    }

    /** 是否显示右下角的缩放角（默认显示）。 */
    public CanvasView setResizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    public CanvasView setOnItemClick(@Nullable ItemClickListener onItemClick) {
        this.onItemClick = onItemClick;
        return this;
    }

    public CanvasView setOnBackgroundClick(@Nullable BackgroundClickListener onBackgroundClick) {
        this.onBackgroundClick = onBackgroundClick;
        return this;
    }

    /** 悬浮在画布上的元素（加到底部居中，通常是 {@code Dock}）。两端建界面时都要加（它是控件树的一部分）。 */
    public CanvasView addOverlay(Widget overlay) {
        addChild(overlay);
        return this;
    }

    /**
     * 浮在画布右侧的卡片位（右上角，离边 {@link UISizes#DOCK_MARGIN}，高度不超过底部悬浮层以上的空间）。
     * 两端建界面时都要加。卡片打开时 {@link #focus}、{@link #fit}、{@link #centerOn} 等只用卡片左边露出的部分。
     */
    public CanvasView addFloatingCard(CardHost card) {
        card.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).top(UISizes.DOCK_MARGIN).right(UISizes.DOCK_MARGIN));
        addChild(card);
        floatingCard = card;
        return this;
    }

    public boolean isMinimapShown() {
        return minimapShown;
    }

    /** 缩略图显隐（客户端、只属于这个打开的界面）。 */
    public void setMinimapShown(boolean shown) {
        this.minimapShown = shown;
    }

    // ==================== 视图 ====================

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float scale() {
        return scale;
    }

    /** 每个世界单位占多少<b>物理</b>像素（缩放 × GUI 缩放），细节层级按它算。 */
    @OnlyIn(Dist.CLIENT)
    public float pixelScale() {
        return scale * (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    @OnlyIn(Dist.CLIENT)
    public CanvasLod lod() {
        return CanvasLod.resolve(pixelScale(), lodSimplifiedPixelScale, lodBlockPixelScale);
    }

    public int viewportX() {
        return getPositionX() + FRAME;
    }

    public int viewportY() {
        return getPositionY() + FRAME;
    }

    public int viewportWidth() {
        return Math.max(0, getSizeWidth() - 2 * FRAME);
    }

    public int viewportHeight() {
        return Math.max(0, getSizeHeight() - 2 * FRAME);
    }

    /**
     * 视口里没被右侧悬浮卡片挡住的宽度（从视口左边算起），卡片没打开时就是视口宽度。
     * 卡片刚打开、还没排版时按最小卡片宽度估计。至少留视口的 1/3。
     */
    public int unobstructedWidth() {
        int vw = viewportWidth();
        if (floatingCard == null || !floatingCard.isOpenOrRequested()) return vw;
        int cardWidth = floatingCard.getSizeWidth() > 0 ? floatingCard.getSizeWidth() : UISizes.POPUP_CONTENT_WIDTH + 2 * UISizes.POPUP_PADDING;
        return Math.max(vw / 3, vw + 2 * FRAME - cardWidth - 2 * UISizes.DOCK_MARGIN);
    }

    /** 视口里看得到的世界范围。 */
    public CanvasRect visibleRect() {
        return new CanvasRect(offsetX, offsetY, viewportWidth() / scale, viewportHeight() / scale);
    }

    public float toWorldX(double screenX) {
        return offsetX + (float) (screenX - viewportX()) / scale;
    }

    public float toWorldY(double screenY) {
        return offsetY + (float) (screenY - viewportY()) / scale;
    }

    public boolean isInViewport(double mouseX, double mouseY) {
        return isMouseOver(viewportX(), viewportY(), viewportWidth(), viewportHeight(), mouseX, mouseY);
    }

    /** 所有图层内容的范围，没有内容为 null（缓存，图层变化时失效）。 */
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

    /** 立即（或动画过渡到）指定视图：视口左上角的世界坐标与缩放。 */
    public void setView(float offsetX, float offsetY, float scale, boolean animated) {
        float targetScale = Mth.clamp(scale, minScale, maxScale);
        if (viewAnimation != null) viewAnimation.cancel();
        viewAnimation = null;
        if (!animated || !isRemote()) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.scale = targetScale;
            clampView();
            return;
        }
        // 缩放按对数插值（视觉上匀速），偏移按"视口中心"插值，缩放途中中心点平滑移动
        float fromCx = this.offsetX + viewportWidth() / (2 * this.scale), fromCy = this.offsetY + viewportHeight() / (2 * this.scale);
        float toCx = offsetX + viewportWidth() / (2 * targetScale), toCy = offsetY + viewportHeight() / (2 * targetScale);
        float fromLog = (float) Math.log(this.scale), toLog = (float) Math.log(targetScale);
        viewAnimation = animations.play(VIEW_ANIMATION, 0, 1, t -> {
            this.scale = (float) Math.exp(Mth.lerp(t, fromLog, toLog));
            this.offsetX = Mth.lerp(t, fromCx, toCx) - viewportWidth() / (2 * this.scale);
            this.offsetY = Mth.lerp(t, fromCy, toCy) - viewportHeight() / (2 * this.scale);
        });
        viewAnimation.onFinished(this::clampView);
    }

    /** 把世界坐标 ({@code worldX}, {@code worldY}) 移到视口中央，缩放不变。 */
    public void centerOn(float worldX, float worldY, boolean animated) {
        setView(worldX - unobstructedWidth() / (2 * scale), worldY - viewportHeight() / (2 * scale), scale, animated);
    }

    /** 把 {@code rect} 移到视口中央；当前缩放下放不下时缩小到正好放下。 */
    public void focus(CanvasRect rect, boolean animated) {
        float target = Math.min(scale, fitScale(rect.inflate(UISizes.SLOT)));
        setView(rect.centerX() - unobstructedWidth() / (2 * target), rect.centerY() - viewportHeight() / (2 * target), target, animated);
    }

    /** {@code rect} 已经整个露在视口里（不被右侧卡片挡住）时不动，否则同 {@link #focus}。 */
    public void reveal(CanvasRect rect, boolean animated) {
        float right = offsetX + unobstructedWidth() / scale, bottom = offsetY + viewportHeight() / scale;
        if (rect.x() >= offsetX && rect.right() <= right && rect.y() >= offsetY && rect.bottom() <= bottom) return;
        focus(rect, animated);
    }

    /** 缩放到正好放下 {@code rect}（四周留 {@code padding} 界面像素）并居中，不超过 {@code maxFitScale}。 */
    public void fit(CanvasRect rect, float padding, float maxFitScale, boolean animated) {
        float s = Math.min(maxFitScale, fitScale(rect, padding));
        setView(rect.centerX() - unobstructedWidth() / (2 * s), rect.centerY() - viewportHeight() / (2 * s), s, animated);
    }

    /** 适应全部内容（最大放大到 1 倍）。 */
    public void fitContent(boolean animated) {
        var bounds = contentBounds();
        if (bounds != null) fit(bounds, UISizes.SLOT / 2f, 1, animated);
    }

    /**
     * 从内容的起点看起：1 倍缩放，内容左上角离视口左上角 {@code padding} 界面像素；内容比视口窄 / 矮时在该方向居中。
     */
    public void showStart(float padding, boolean animated) {
        var bounds = contentBounds();
        if (bounds == null) return;
        float s = Mth.clamp(1, minScale, maxScale);
        float vw = unobstructedWidth() / s, vh = viewportHeight() / s;
        float x = bounds.width() + 2 * padding / s <= vw ? bounds.centerX() - vw / 2 : bounds.x() - padding / s;
        float y = bounds.height() + 2 * padding / s <= vh ? bounds.centerY() - vh / 2 : bounds.y() - padding / s;
        setView(x, y, s, animated);
    }

    /** 以视口（没被右侧卡片挡住的部分）中心为基准缩放 {@code factor} 倍（按钮用）。 */
    public void zoomBy(float factor, boolean animated) {
        float target = Mth.clamp(scale * factor, minScale, maxScale);
        int width = unobstructedWidth();
        float cx = offsetX + width / (2 * scale), cy = offsetY + viewportHeight() / (2 * scale);
        setView(cx - width / (2 * target), cy - viewportHeight() / (2 * target), target, animated);
    }

    public boolean canZoomIn() {
        return scale < maxScale - 1e-4f;
    }

    public boolean canZoomOut() {
        return scale > minScale + 1e-4f;
    }

    /** 以屏幕点 ({@code screenX}, {@code screenY}) 为中心缩放：鼠标下的世界点保持不动。 */
    private void zoomAt(double screenX, double screenY, float factor) {
        float target = Mth.clamp(scale * factor, minScale, maxScale);
        if (Math.abs(target - scale) < 1e-5f) return;
        float wx = toWorldX(screenX), wy = toWorldY(screenY);
        float localX = (float) (screenX - viewportX()), localY = (float) (screenY - viewportY());
        setView(wx - localX / target, wy - localY / target, target, false);
    }

    private float fitScale(CanvasRect rect) {
        return fitScale(rect, 0);
    }

    private float fitScale(CanvasRect rect, float padding) {
        float w = Math.max(1, rect.width()), h = Math.max(1, rect.height());
        float s = Math.min((unobstructedWidth() - 2 * padding) / w, (viewportHeight() - 2 * padding) / h);
        return Mth.clamp(s, minScale, maxScale);
    }

    /** 平移范围：内容最远拖到刚好移出视口（最右边的内容移出左边缘、最左边的移出右边缘，上下同理）。 */
    private void clampView() {
        var bounds = contentBounds();
        if (bounds == null || viewportWidth() <= 0 || viewportHeight() <= 0) return;
        float viewW = viewportWidth() / scale, viewH = viewportHeight() / scale;
        offsetX = Mth.clamp(offsetX, bounds.x() - viewW, bounds.right());
        offsetY = Mth.clamp(offsetY, bounds.y() - viewH, bounds.bottom());
    }

    private void cancelViewAnimation() {
        if (viewAnimation != null) viewAnimation.cancel();
        viewAnimation = null;
    }

    // ==================== 尺寸 ====================

    private void applySize() {
        int w = lockedWidth > 0 ? lockedWidth : userWidth > 0 ? userWidth : preferredWidth;
        int h = lockedHeight > 0 ? lockedHeight : userHeight > 0 ? userHeight : preferredHeight;
        layout(l -> l.size(w, h));
    }

    /**
     * 默认尺寸按屏幕撑大（客户端打开界面时算一次）：宽 = 屏幕宽 − {@code reservedWidth}，
     * 高 = 屏幕高 × (1 − {@link UISizes#WINDOW_BOTTOM_SCREEN_MARGIN}) − {@code reservedHeight}，
     * 不小于首选尺寸、不大于 {@code maxWidth}/{@code maxHeight}。{@code reserved} 是同一界面里其他部分
     * （窗口边距、标题栏、标签栏、弹出面板……）要占的空间。锁定的尺寸优先。两端尺寸可以不同（控件树不变）。
     */
    public CanvasView fillScreen(int reservedWidth, int reservedHeight, int maxWidth, int maxHeight) {
        this.screenFill = new int[] { reservedWidth, reservedHeight, maxWidth, maxHeight };
        return this;
    }

    /** 客户端：按屏幕算默认尺寸。 */
    @OnlyIn(Dist.CLIENT)
    private void applyScreenFill() {
        if (screenFill == null) return;
        var window = Minecraft.getInstance().getWindow();
        int w = window.getGuiScaledWidth() - screenFill[0];
        int h = Math.round(window.getGuiScaledHeight() * (1 - UISizes.WINDOW_BOTTOM_SCREEN_MARGIN)) - screenFill[1];
        preferredWidth = Mth.clamp(w, preferredWidth, Math.max(preferredWidth, screenFill[2]));
        preferredHeight = Mth.clamp(h, preferredHeight, Math.max(preferredHeight, screenFill[3]));
        applySize();
    }

    /** 首选尺寸（未拖动、未锁定时使用）。 */
    public CanvasView setPreferredSize(int width, int height) {
        this.preferredWidth = width;
        this.preferredHeight = height;
        applySize();
        return this;
    }

    public boolean isLocked() {
        return lockedWidth > 0 && lockedHeight > 0;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        if (!isRemote()) return;
        // 不可缩放的画布（如 EMI 配方页里尺寸固定的）不套用锁定的尺寸
        applyScreenFill();
        if (resizable) loadLockedSize();
        if (!sceneBuilt) rebuildScene();
    }

    /** 客户端：读锁定的尺寸，超出屏幕或过小时不应用（能拖多大由外壳决定，这里只防止屏幕变小后放不下）。 */
    @OnlyIn(Dist.CLIENT)
    private void loadLockedSize() {
        int w = LockedScrollerSizes.width(id), h = LockedScrollerSizes.height(id);
        var window = Minecraft.getInstance().getWindow();
        boolean fits = w >= MIN_SIZE && h >= MIN_SIZE && w <= window.getGuiScaledWidth() && h <= window.getGuiScaledHeight();
        lockedWidth = fits ? w : -1;
        lockedHeight = fits ? h : -1;
        applySize();
    }

    /** 视口尺寸变了（拖拽缩放、锁定、窗口重排）：保持视口中心的世界点不动。 */
    private void onViewportResized() {
        int w = viewportWidth(), h = viewportHeight();
        if (lastViewportWidth > 0 && lastViewportHeight > 0 && (w != lastViewportWidth || h != lastViewportHeight)) {
            offsetX += (lastViewportWidth - w) / (2 * scale);
            offsetY += (lastViewportHeight - h) / (2 * scale);
            clampView();
        }
        lastViewportWidth = w;
        lastViewportHeight = h;
    }

    // ==================== 绘制 ====================

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        UITheme.CANVAS.draw(graphics, mouseX, mouseY, x, y, w, h);
        int vx = viewportX(), vy = viewportY(), vw = viewportWidth(), vh = viewportHeight();
        if (vw <= 0 || vh <= 0) return;
        if (!sceneBuilt) rebuildScene();
        if (!viewInitialized) {
            viewInitialized = true;
            lastViewportWidth = vw;
            lastViewportHeight = vh;
            if (initialView != null) initialView.accept(this);
            else showStart(UISizes.SLOT / 2f, false);
        }
        onViewportResized();
        if (floatingCard != null) floatingCard.setMaxHeight(floatingCardMaxHeight());
        animations.updateFrame();
        updateMinimapRect();

        boolean overOverlay = isOverOverlay(mouseX, mouseY);
        boolean inside = isInViewport(mouseX, mouseY) && !overOverlay && !isOverMinimap(mouseX, mouseY) && !isOverGrip(mouseX, mouseY);
        hovered = inside && !panning && !resizing ? pick(toWorldX(mouseX), toWorldY(mouseY)) : null;

        var pose = graphics.pose();
        var matrix = pose.last().pose();
        matrix.transform(scissorMin.set(vx, vy, 0, 1));
        matrix.transform(scissorMax.set(vx + vw, vy + vh, 0, 1));
        graphics.enableScissor(Math.round(scissorMin.x), Math.round(scissorMin.y), Math.round(scissorMax.x), Math.round(scissorMax.y));
        if (painter == null) painter = new CanvasPainter();
        if (grid != null) {
            // 网格按屏幕坐标画：画笔先以屏幕空间开始本帧
            painter.begin(graphics, 1, CanvasLod.FULL, CanvasRect.of(vx, vy, vw, vh), Float.NaN, Float.NaN, null);
            drawGrid(graphics, grid, vx, vy, vw, vh);
        }

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
        // 选中框在屏幕空间画（与物品槽的选中框同一个），任何缩放下都是同样粗细
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
        // 悬浮层画在内容之上：鼠标在它外面时按"不在任何子控件上"绘制，内容下的按钮不会被悬停高亮
        super.drawInBackground(graphics, overOverlay ? mouseX : OUTSIDE, overOverlay ? mouseY : OUTSIDE, partialTicks);
        pose.popPose();
        graphics.disableScissor();

        if (resizable) {
            pose.pushPose();
            pose.translate(0, 0, OVERLAY_Z + 10);
            UITheme.drawResizeGrip(graphics, x + w, y + h, resizing || isOverGrip(mouseX, mouseY), isLocked());
            pose.popPose();
        }
    }

    /** 背景网格：屏幕空间按世界坐标对齐画 1 像素线（见 {@link CanvasGrid}），所有线攒成一批提交。 */
    @OnlyIn(Dist.CLIENT)
    private void drawGrid(GuiGraphics graphics, CanvasGrid grid, int vx, int vy, int vw, int vh) {
        var levels = grid.levels(scale);
        for (int l = 0; l < levels.size(); l++) {
            var level = levels.get(l);
            int color = level.color();
            float cell = level.cellSize();
            if ((color >>> 24) == 0 || cell * scale < 2) continue;
            long firstX = (long) Math.floor(offsetX / cell), lastX = (long) Math.ceil((offsetX + vw / scale) / cell);
            long firstY = (long) Math.floor(offsetY / cell), lastY = (long) Math.ceil((offsetY + vh / scale) / cell);
            if (lastX - firstX > MAX_GRID_LINES || lastY - firstY > MAX_GRID_LINES) continue;
            int skip = level.skipEvery();
            for (long i = firstX; i <= lastX; i++) {
                if (skip > 0 && Math.floorMod(i, skip) == 0) continue;
                float sx = PixelSnap.snap(vx + (i * cell - offsetX) * scale);
                painter.fill(sx, vy, sx + 1, vy + vh, color);
            }
            for (long i = firstY; i <= lastY; i++) {
                if (skip > 0 && Math.floorMod(i, skip) == 0) continue;
                float sy = PixelSnap.snap(vy + (i * cell - offsetY) * scale);
                painter.fill(vx, sy, vx + vw, sy + 1, color);
            }
        }
        painter.flush();
    }

    /** 右侧卡片的高度上限：从上边距到底部悬浮层（再隔一个边距）；没有悬浮层时到下边距。 */
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

    /** 按内容范围与视口尺寸算缩略图的屏幕范围（每帧一次，悬停、点击沿用）；不显示或没有内容时宽为 0。 */
    private void updateMinimapRect() {
        var bounds = contentBounds();
        if (!minimapShown || bounds == null) {
            minimap[2] = 0;
            return;
        }
        int vw = viewportWidth(), vh = viewportHeight();
        int maxW = Mth.clamp(vw / 3, UISizes.CANVAS_MINIMAP_MIN_WIDTH, UISizes.CANVAS_MINIMAP_MAX_WIDTH);
        int maxH = Mth.clamp(vh / 3, UISizes.CANVAS_MINIMAP_MIN_HEIGHT, UISizes.CANVAS_MINIMAP_MAX_HEIGHT);
        float s = Math.min(maxW / Math.max(1, bounds.width()), maxH / Math.max(1, bounds.height()));
        int w = Math.max(8, Math.round(bounds.width() * s)) + 2, h = Math.max(8, Math.round(bounds.height() * s)) + 2;
        // 右侧卡片打开时让到卡片左边（露出部分的右端已与卡片隔开一点）
        int right = viewportX() + unobstructedWidth() - (unobstructedWidth() < vw ? 0 : UISizes.CANVAS_MINIMAP_MARGIN);
        minimap[0] = right - w;
        minimap[1] = viewportY() + UISizes.CANVAS_MINIMAP_MARGIN;
        minimap[2] = w;
        minimap[3] = h;
    }

    private boolean isOverMinimap(double mouseX, double mouseY) {
        return minimap[2] > 0 && isMouseOver(minimap[0], minimap[1], minimap[2], minimap[3], mouseX, mouseY);
    }

    /** 缩略图里世界→屏幕的比例（内容居中放进缩略图内框）。 */
    private float minimapScale(CanvasRect bounds) {
        return Math.min((minimap[2] - 2) / Math.max(1, bounds.width()), (minimap[3] - 2) / Math.max(1, bounds.height()));
    }

    @OnlyIn(Dist.CLIENT)
    private void drawMinimap(GuiGraphics graphics) {
        var bounds = contentBounds();
        if (bounds == null) return;
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
        // 视口框，夹在缩略图范围内
        float right = offsetX + viewportWidth() / scale, bottom = offsetY + viewportHeight() / scale;
        int x0 = Mth.clamp(Math.round(ox + (offsetX - bounds.x()) * s), mx, mx + mw - 2);
        int y0 = Mth.clamp(Math.round(oy + (offsetY - bounds.y()) * s), my, my + mh - 2);
        int x1 = Mth.clamp(Math.round(ox + (right - bounds.x()) * s), x0 + 2, mx + mw);
        int y1 = Mth.clamp(Math.round(oy + (bottom - bounds.y()) * s), y0 + 2, my + mh);
        UITheme.drawOutline(graphics, x0, y0, x1 - x0, y1 - y0, UITheme.CANVAS_MINIMAP_VIEWPORT);
    }

    /** 缩略图上的点对应的世界坐标，移到视口中央。 */
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

    /** 鼠标下的项（只算视口内、不被悬浮层 / 缩略图挡住的），没有为 null。 */
    @Nullable
    public CanvasItem itemAt(double mouseX, double mouseY) {
        if (!isInViewport(mouseX, mouseY) || isOverOverlay(mouseX, mouseY) || isOverMinimap(mouseX, mouseY) || isOverGrip(mouseX, mouseY)) return null;
        return pick(toWorldX(mouseX), toWorldY(mouseY));
    }

    /** 当前悬停的项（客户端，每帧绘制时更新）。 */
    @Nullable
    public CanvasItem getHovered() {
        return hovered;
    }

    private boolean isOverOverlay(double mouseX, double mouseY) {
        for (var widget : widgets) {
            if (widget.isVisible() && widget.isMouseOverElement(mouseX, mouseY)) return true;
        }
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (gui == null || gui.getModularUIGui() == null || panning || resizing) return;
        if (isOverGrip(mouseX, mouseY)) {
            var lines = isLocked() ?
                    List.<Component>of(Component.translatable(GRIP_LOCKED), Component.translatable(GRIP_UNLOCK).withStyle(ChatFormatting.GRAY)) :
                    List.<Component>of(Component.translatable(GRIP_RESIZE), Component.translatable(GRIP_LOCK).withStyle(ChatFormatting.GRAY));
            gui.getModularUIGui().setHoverTooltip(lines, ItemStack.EMPTY, null, null);
            return;
        }
        // 与本帧背景绘制是同一个鼠标位置：直接用刚算出的悬停项，不再命中测试
        if (hovered == null) return;
        var tooltip = hovered.tooltip();
        if (!tooltip.isEmpty()) gui.getModularUIGui().setHoverTooltip(tooltip, ItemStack.EMPTY, null, null);
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        if (!isVisible()) return null;
        if (isOverOverlay(mouseX, mouseY)) return super.getXEIIngredientOverMouse(mouseX, mouseY);
        var item = itemAt(mouseX, mouseY);
        return item == null ? null : item.ingredient();
    }

    // ==================== 鼠标 ====================

    private boolean isOverGrip(double mouseX, double mouseY) {
        if (!resizable) return false;
        int right = getPositionX() + getSizeWidth(), bottom = getPositionY() + getSizeHeight();
        return mouseX >= right - UITheme.RESIZE_GRIP_SIZE && mouseX < right && mouseY >= bottom - UITheme.RESIZE_GRIP_SIZE && mouseY < bottom;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isActive()) return false;
        if (isOverGrip(mouseX, mouseY)) {
            if (button == 1) {
                toggleLock();
            } else if (button == 0 && !isLocked()) {
                startResize(mouseX, mouseY);
            }
            return true;
        }
        if (isOverOverlay(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (!isInViewport(mouseX, mouseY)) return false;
        cancelViewAnimation();
        if (isOverMinimap(mouseX, mouseY)) {
            if (button == 0) {
                minimapDragging = true;
                navigateMinimap(mouseX, mouseY);
            }
            return true;
        }
        pressButton = button;
        pressX = mouseX;
        pressY = mouseY;
        pressOffsetX = offsetX;
        pressOffsetY = offsetY;
        panning = false;
        pressedItem = pick(toWorldX(mouseX), toWorldY(mouseY));
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizing) {
            resizeTo(mouseX, mouseY);
            return true;
        }
        if (minimapDragging) {
            navigateMinimap(mouseX, mouseY);
            return true;
        }
        if (pressButton >= 0) {
            double dx = mouseX - pressX, dy = mouseY - pressY;
            if (!panning && allowPan && (pressButton == 0 || pressButton == 2) && Math.abs(dx) + Math.abs(dy) > DRAG_THRESHOLD) panning = true;
            if (panning) {
                offsetX = pressOffsetX - (float) dx / scale;
                offsetY = pressOffsetY - (float) dy / scale;
                clampView();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
        if (minimapDragging) {
            minimapDragging = false;
            return true;
        }
        if (pressButton >= 0) {
            boolean wasPanning = panning;
            int pressed = pressButton;
            var item = pressedItem;
            pressButton = -1;
            panning = false;
            pressedItem = null;
            if (!wasPanning && pressed == button && isInViewport(mouseX, mouseY)) {
                var released = pick(toWorldX(mouseX), toWorldY(mouseY));
                if (item != null && released == item) {
                    if (onItemClick != null) {
                        onItemClick.onClick(item, button);
                        playButtonClickSound();
                    }
                } else if (item == null && released == null && onBackgroundClick != null) {
                    onBackgroundClick.onClick(toWorldX(mouseX), toWorldY(mouseY), button);
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isVisible()) return false;
        if (isOverOverlay(mouseX, mouseY)) return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        if (!isInViewport(mouseX, mouseY) || !allowZoom || wheelDelta == 0) return false;
        cancelViewAnimation();
        zoomAt(mouseX, mouseY, wheelDelta > 0 ? WHEEL_ZOOM_STEP : 1 / WHEEL_ZOOM_STEP);
        return true;
    }

    // ==================== 缩放角 ====================

    @OnlyIn(Dist.CLIENT)
    private void startResize(double mouseX, double mouseY) {
        resizing = true;
        resizeStartX = mouseX;
        resizeStartY = mouseY;
        resizeStartWidth = getSizeWidth();
        resizeStartHeight = getSizeHeight();
        // 界面有尺寸上限（一般是屏幕 2/3）：这次拖拽最多再长外壳给的余量（已超过时只能缩小）
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

    /** 右键：锁定当前尺寸（本地保存，重开界面沿用）／解锁（回到默认或本次拖出来的尺寸）。 */
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
