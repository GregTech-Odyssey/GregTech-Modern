package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.ILayoutHost;
import com.gregtechceu.gtceu.uipro.animation.Animation;
import com.gregtechceu.gtceu.uipro.animation.AnimationEngine;
import com.gregtechceu.gtceu.uipro.animation.Eases;
import com.gregtechceu.gtceu.uipro.animation.PixelSnap;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

/**
 * GTM 的机器界面外壳：主页 + 侧边子页面导航、翻页、左侧配置面板、右侧提示面板。
 * <p>
 * 在原有功能上嫁接了新式 UI 框架（{@code com.gregtechceu.gtceu.uipro}）需要的外壳能力，原有页面与接口不受影响：
 * <ul>
 * <li>布局外壳（{@link ILayoutHost}）：新式页面（一棵 {@code UIElement} 树）尺寸变了，按本类原来的摆放规则重算窗口尺寸，
 * 不重建页面——窗口随内容动态变化；</li>
 * <li>窗口位置 = 基准位置（{@link #setWindowBasePosition}）+ 偏移：拖拽滚动区缩放时钉住屏幕位置，松手后、切换页面、内容变化时
 * 用缓动动画移到新位置（{@link #relayoutAnimated}）；打开阶段直接摆放；</li>
 * <li>{@link #detectAndSendChanges} 捕获单个页面的异常并记日志，不让一个页面出错打断整个界面的同步；</li>
 * <li>可在构造后立即执行一段初始化（带 {@code init} 的构造器）。</li>
 * </ul>
 * 新式外观（顶部标签、窗口内标题栏、弹出面板等）见子类 {@code com.gregtechceu.gtceu.uipro.window.MachineWindow}。
 */
public class FancyMachineUIWidget extends WidgetGroup implements ILayoutHost {

    @Getter
    protected final TitleBarWidget titleBar;
    @Getter
    protected final VerticalTabsWidget sideTabsWidget;
    @Getter
    protected final PageContainer pageContainer;
    @Getter
    protected final PageSwitcher pageSwitcher;
    @Getter
    protected final ConfiguratorPanel configuratorPanel;
    @Getter
    protected final TooltipsPanel tooltipsPanel;
    @Nullable
    protected final PlayerInventoryWidget playerInventory;
    @Getter
    @Setter
    protected int border = 4;
    @Getter
    protected final IFancyUIProvider mainPage;
    /*
     * Current Page: The page visible in the UI
     * Current Home Page: The currently selected multiblock part's home page.
     */
    @Getter
    protected IFancyUIProvider currentPage;
    @Getter
    protected IFancyUIProvider currentHomePage;
    @Getter
    protected List<IFancyUIProvider> allPages;
    @Getter
    protected Deque<NavigationEntry> previousPages = new ArrayDeque<>();

    protected record NavigationEntry(IFancyUIProvider page, IFancyUIProvider homePage, Runnable onNavigation) {}

    // ==================== 新式框架的外壳能力 ====================

    /// 窗口移到新位置的动画（松手回中、切页、内容变化）
    private static final Animation SETTLE = Animation.of(0.3f, Eases.CUBIC_OUT);
    /// 打开阶段：窗口第一次绘制起这么久内（同步数据陆续到达、页面几次变高）的重新摆放直接生效，不做动画
    private static final long OPENING_NANOS = 500_000_000L;

    /** 当前页是否显示玩家背包（页面尺寸变化时沿用）。 */
    protected boolean pageShowsInventory;
    private boolean relayouting;
    private final AnimationEngine animations = new AnimationEngine();
    private int baseX, baseY;
    private float offsetX, offsetY;
    private boolean anchored;
    private int anchorX, anchorY;
    /// 窗口第一次绘制的时间，-1 为还没画过
    private long firstFrameNanos = -1;
    @Nullable
    private AnimationEngine.Playback settle;

    public FancyMachineUIWidget(IFancyUIProvider mainPage, int width, int height) {
        super(0, 0, width, height);
        this.mainPage = mainPage;
        addWidget(this.pageContainer = new PageContainer(0, 0, width, height));
        if (mainPage.hasPlayerInventory()) {
            addWidget(this.playerInventory = new PlayerInventoryWidget());
            this.playerInventory.setSelfPosition(new Position(2, height - 86));
            this.playerInventory.setBackground((IGuiTexture) null);
        } else {
            playerInventory = null;
        }
        addWidget(this.titleBar = new TitleBarWidget(width, this::navigateBack, this::openPageSwitcher));
        addWidget(this.sideTabsWidget = new VerticalTabsWidget(this::navigate, -20, 0, 24, height));
        addWidget(this.tooltipsPanel = new TooltipsPanel());
        addWidget(this.configuratorPanel = new ConfiguratorPanel(-(24 + 2), height));
        this.pageSwitcher = new PageSwitcher(this::switchPage);
        setBackground(GuiTextures.BACKGROUND.copy().setColor(Long.decode(ConfigHolder.INSTANCE.client.defaultUIColor).intValue() | -16777216));
    }

    /** 构造后立即执行 {@code init}（如给页面准备数据）。 */
    public FancyMachineUIWidget(IFancyUIProvider mainPage, int width, int height, Runnable init) {
        this(mainPage, width, height);
        init.run();
    }

    @Override
    public void initWidget() {
        super.initWidget();
        if (this.playerInventory != null) {
            this.playerInventory.setPlayer(gui.entityPlayer);
        }
        this.allPages = Stream.concat(Stream.of(this.mainPage), this.mainPage.getSubTabs().stream()).toList();
        performNavigation(this.mainPage, this.mainPage);
    }

    ////////////////////////////////////////
    // ********* NAVIGATION *********//
    ////////////////////////////////////////
    protected void navigate(IFancyUIProvider newPage) {
        navigate(newPage, this.currentHomePage);
    }

    protected void navigate(IFancyUIProvider nextPage, IFancyUIProvider nextHomePage) {
        if (nextPage != mainPage) {
            if (!this.previousPages.isEmpty() && this.previousPages.peek().page == nextPage) {
                // In case the user manually navigates back one step, just remove it from the navigation stack
                this.previousPages.pop();
            } else if (this.currentPage != null) {
                this.previousPages.push(new NavigationEntry(this.currentPage, this.currentHomePage, GTUtil.NOOP));
            }
        } else {
            this.previousPages.clear();
        }
        performNavigation(nextPage, nextHomePage);
    }

    protected void navigateBack(ClickData clickData) {
        NavigationEntry navigationEntry = previousPages.pop();
        performNavigation(navigationEntry.page, navigationEntry.homePage);
        navigationEntry.onNavigation.run();
    }

    protected void performNavigation(IFancyUIProvider nextPage, IFancyUIProvider nextHomePage) {
        if (currentHomePage != nextHomePage) setupSideTabs(nextHomePage);
        this.currentPage = nextPage;
        this.currentHomePage = nextHomePage;
        if (currentPage != currentHomePage) {
            // Ensure the home page's basic layout is applied before navigating to another page:
            setupFancyUI(currentHomePage);
        }
        setupFancyUI(nextPage, nextPage.hasPlayerInventory());
    }

    ///////////////////////////////////////////////
    // *********** PAGE SWITCHER ***********//
    ///////////////////////////////////////////////
    protected void openPageSwitcher(ClickData clickData) {
        pageSwitcher.setPageList(allPages, currentHomePage);
        // If we're in another tab of the current page, ensure nav to its main tab when closing the page switcher:
        if (currentPage != currentHomePage && !previousPages.isEmpty()) {
            previousPages.pop();
        }
        this.sideTabsWidget.setVisible(false);
        this.sideTabsWidget.setActive(false);
        this.previousPages.push(new NavigationEntry(currentHomePage, currentHomePage, () -> {
            sideTabsWidget.setVisible(true);
            sideTabsWidget.setActive(true);
        }));
        this.currentPage = this.pageSwitcher;
        this.currentHomePage = this.pageSwitcher;
        setupFancyUI(this.pageSwitcher);
    }

    protected void switchPage(IFancyUIProvider nextHomePage) {
        // Ensure that the back button always leads back to the main page:
        this.currentHomePage = mainPage;
        this.currentPage = mainPage;
        this.previousPages.clear();
        sideTabsWidget.setVisible(true);
        sideTabsWidget.setActive(true);
        setupSideTabs(this.currentHomePage);
        navigate(nextHomePage, nextHomePage);
    }

    //////////////////////////////////////////////
    // *********** UI RENDERING ***********//
    //////////////////////////////////////////////
    protected void setupFancyUI(IFancyUIProvider fancyUI) {
        this.setupFancyUI(fancyUI, fancyUI.hasPlayerInventory());
    }

    /** 建页并摆放；切换页面时窗口从原位置用动画移到新位置（见 {@link #relayoutAnimated}）。 */
    protected void setupFancyUI(IFancyUIProvider fancyUI, boolean showInventory) {
        pageShowsInventory = showInventory;
        relayoutAnimated(() -> layoutFancyUI(fancyUI, showInventory));
    }

    private void layoutFancyUI(IFancyUIProvider fancyUI, boolean showInventory) {
        clearUI();
        sideTabsWidget.selectTab(fancyUI);
        titleBar.updateState(currentHomePage, !this.previousPages.isEmpty(), this.allPages.size() > 1 && this.currentPage != this.pageSwitcher);
        var page = fancyUI.createMainPage(this);
        // layout
        var size = new Size(Math.max(172, page.getSize().width + border * 2), Math.max(86, page.getSize().height + border * 2));
        setSize(new Size(size.width, size.height + (!showInventory || playerInventory == null ? 0 : playerInventory.getSize().height)));
        if (GTCEu.isClientSide() && getGui() != null) {
            getGui().setSize(getSize().width, getSize().height);
        }
        this.sideTabsWidget.setSize(new Size(24, 8 + (sideTabsWidget.getSubTabs().size() + 1) * 24));
        this.pageContainer.setSize(size);
        this.tooltipsPanel.setSelfPosition(new Position(size.width + 2, 2));
        setupInventoryPosition(showInventory, size);
        // setup
        this.pageContainer.addWidget(page);
        page.setSelfPosition(new Position((pageContainer.getSize().width - page.getSize().width) / 2, (pageContainer.getSize().height - page.getSize().height) / 2));
        configuratorPanel.setAvailableHeight(getGui().getHeight() - 4 - getSideTabsBottom());
        fancyUI.attachConfigurators(configuratorPanel);
        configuratorPanel.setSelfPosition(new Position(-configuratorPanel.getSize().width - 2, getGui().getHeight() - configuratorPanel.getSize().height - 4));
        fancyUI.attachTooltips(tooltipsPanel);
        titleBar.setSize(new Size(this.getSize().width, titleBar.getSize().height));
        applyWindowPosition();
    }

    // 算左上角标签栏的底部坐标
    protected int getSideTabsBottom() {
        return sideTabsWidget.getSelfPosition().y + 8 + (sideTabsWidget.getSubTabs().size() + 1) * 24;
    }

    private void setupInventoryPosition(boolean showInventory, Size parentSize) {
        if (this.playerInventory == null) return;
        this.playerInventory.setSelfPosition(new Position((parentSize.width - playerInventory.getSize().width) / 2, parentSize.height));
        this.playerInventory.setActive(showInventory);
        this.playerInventory.setVisible(showInventory);
    }

    protected void clearUI() {
        this.pageContainer.clearAllWidgets();
        this.configuratorPanel.clear();
        this.tooltipsPanel.clear();
    }

    protected void setupSideTabs(IFancyUIProvider currentHomePage) {
        this.sideTabsWidget.clearSubTabs();
        currentHomePage.attachSideTabs(sideTabsWidget);
    }

    @Nullable
    public PlayerInventoryWidget getPlayerInventory() {
        return this.playerInventory;
    }

    // ==================== 布局外壳：页面尺寸变化 ====================

    @Override
    public void onContentResized(Widget root) {
        if (relayouting || root.getParent() != pageContainer || !pageContainer.widgets.contains(root)) return;
        relayouting = true;
        try {
            relayoutAnimated(() -> relayoutPage(root, pageShowsInventory));
        } finally {
            relayouting = false;
        }
    }

    /** 页面尺寸变了：按 {@link #setupFancyUI} 的规则重算窗口尺寸与各部件位置（不重建页面、不增删控件）。 */
    protected void relayoutPage(Widget page, boolean showInventory) {
        var size = new Size(Math.max(172, page.getSizeWidth() + border * 2), Math.max(86, page.getSizeHeight() + border * 2));
        setSize(new Size(size.width, size.height + (!showInventory || playerInventory == null ? 0 : playerInventory.getSizeHeight())));
        if (isRemote() && getGui() != null) resizeGui();
        pageContainer.setSize(size);
        tooltipsPanel.setSelfPosition(new Position(size.width + 2, 2));
        if (playerInventory != null) playerInventory.setSelfPosition(new Position((size.width - playerInventory.getSizeWidth()) / 2, size.height));
        page.setSelfPosition(new Position((size.width - page.getSizeWidth()) / 2, (size.height - page.getSizeHeight()) / 2));
        if (getGui() != null) {
            configuratorPanel.setAvailableHeight(getGui().getHeight() - 4 - getSideTabsBottom());
            // 可用高度变了要重排（不传参数即只重排），否则每列个数、行位置沿用旧值
            configuratorPanel.attachConfigurators();
            configuratorPanel.setSelfPosition(new Position(-configuratorPanel.getSizeWidth() - 2, getGui().getHeight() - configuratorPanel.getSizeHeight() - 4));
        }
        titleBar.setSize(new Size(getSizeWidth(), titleBar.getSizeHeight()));
        applyWindowPosition();
    }

    /// LDLib 的 ModularUI.setSize 只存在于客户端（专用服务器上被剥掉）
    @OnlyIn(Dist.CLIENT)
    private void resizeGui() {
        getGui().setSize(getSizeWidth(), getSizeHeight());
    }

    // ==================== 窗口位置：锚定与回位动画 ====================

    /** 子类按自己的规则算出的窗口位置（相对界面左上角）；实际位置再加上锚定 / 动画的偏移。 */
    protected void setWindowBasePosition(int x, int y) {
        baseX = x;
        baseY = y;
        applyWindowPosition();
    }

    /** 锚定中：偏移取"锚定时的屏幕位置 − 现在的基准位置"，窗口看起来不动；否则按当前偏移（动画中逐帧趋近 0）。 */
    protected void applyWindowPosition() {
        if (anchored) {
            offsetX = anchorX - (getPositionX() - getSelfPositionX() + baseX);
            offsetY = anchorY - (getPositionY() - getSelfPositionY() + baseY);
        }
        var position = new Position(baseX + Math.round(offsetX), baseY + Math.round(offsetY));
        if (!position.equals(getSelfPosition())) setSelfPosition(position);
    }

    @Override
    public void beginInteractiveResize() {
        if (settle != null) settle.cancel();
        settle = null;
        anchored = true;
        anchorX = getPositionX();
        anchorY = getPositionY();
    }

    @Override
    public void endInteractiveResize() {
        if (!anchored) return;
        anchored = false;
        settleFrom(getPositionX(), getPositionY());
    }

    /**
     * 窗口因页面变化重新摆放（切换页面、页面内容变高变矮、弹出面板挤开窗口）：先照常摆放，
     * 再从变化前的屏幕位置用动画移到新位置。拖拽缩放中（位置钉住）、打开阶段（首屏数据到达引起的变化）直接摆放。
     */
    protected final void relayoutAnimated(Runnable relayout) {
        if (anchored || !isRemote() || isOpening()) {
            relayout.run();
            return;
        }
        int oldX = getPositionX(), oldY = getPositionY();
        relayout.run();
        settleFrom(oldX, oldY);
    }

    /** 界面还在打开阶段（没画过，或第一次绘制后不到 {@link #OPENING_NANOS}）：玩家看到的应该是最终布局，不做动画。 */
    protected boolean isOpening() {
        return firstFrameNanos < 0 || System.nanoTime() - firstFrameNanos < OPENING_NANOS;
    }

    /** 从屏幕位置 ({@code fromX}, {@code fromY}) 缓动到当前规则下的位置（偏移归零）。 */
    private void settleFrom(int fromX, int fromY) {
        if (settle != null) settle.cancel();
        settle = null;
        offsetX = 0;
        offsetY = 0;
        applyWindowPosition();
        float dx = fromX - getPositionX(), dy = fromY - getPositionY();
        if (dx == 0 && dy == 0) return;
        offsetX = dx;
        offsetY = dy;
        applyWindowPosition();
        settle = animations.play(SETTLE, 1, 0, k -> {
            offsetX = dx * k;
            offsetY = dy * k;
            applyWindowPosition();
        });
    }

    /** 拖拽缩放的余量：整个窗口不超过屏幕的 {@link UISizes#MAX_WINDOW_SCREEN_RATIO}。 */
    @Override
    public int resizeAllowance(boolean vertical) {
        if (getGui() == null || !isRemote()) return Integer.MAX_VALUE;
        return clientAllowance(vertical);
    }

    @OnlyIn(Dist.CLIENT)
    private int clientAllowance(boolean vertical) {
        var window = Minecraft.getInstance().getWindow();
        int screen = vertical ? window.getGuiScaledHeight() : window.getGuiScaledWidth();
        // 按窗口本身算（新式外壳的界面尺寸会为定位加大，不代表窗口大小）
        int current = vertical ? getSizeHeight() : getSizeWidth();
        return maxWindowExtent(vertical, screen) - current;
    }

    /** 拖拽缩放时窗口的最大宽 / 高（{@code screen} 为屏幕的宽 / 高）：默认屏幕的 {@link UISizes#MAX_WINDOW_SCREEN_RATIO}。 */
    @OnlyIn(Dist.CLIENT)
    protected int maxWindowExtent(boolean vertical, int screen) {
        return (int) (screen * UISizes.MAX_WINDOW_SCREEN_RATIO);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (firstFrameNanos < 0) firstFrameNanos = System.nanoTime();
        animations.updateFrame();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(PixelSnap.residual(offsetX), PixelSnap.residual(offsetY), 0);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        pose.popPose();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(PixelSnap.residual(offsetX), PixelSnap.residual(offsetY), 0);
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        pose.popPose();
    }

    /** 单个页面同步出错时记日志，不让它打断整个界面的同步。 */
    @Override
    public void detectAndSendChanges() {
        try {
            super.detectAndSendChanges();
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to sync fancy machine UI", e);
        }
    }
}
