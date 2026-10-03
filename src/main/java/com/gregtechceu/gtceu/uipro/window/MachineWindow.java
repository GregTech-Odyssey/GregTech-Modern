package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineSubWindowFactory;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.OutputSideConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.PartCellsConfigurator;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.animation.ColorMath;
import com.gregtechceu.gtceu.uipro.animation.UIClock;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.data.UICodecs;
import com.gregtechceu.gtceu.uipro.data.UIStructure;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.CalloutBubble;
import com.gregtechceu.gtceu.uipro.elements.ItemTitle;
import com.gregtechceu.gtceu.uipro.elements.ItemView;
import com.gregtechceu.gtceu.uipro.render.UILayers;
import com.gregtechceu.gtceu.uipro.render.UIPixels;
import com.gregtechceu.gtceu.uipro.render.UIStates;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.ThemeSkin;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.UIPreferences;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.config.EmiConfig;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 机器界面外壳（新式界面的示范实现）：在 GTM {@link com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget}
 * 的导航逻辑（侧边标签页、翻页、左侧配置按钮）之上，重写布局与外观。
 *
 * <pre>
 *            [主页][页2][页3]              ← 页面标签：窗口顶上横排，选中的与窗口连成一体
 *  [配置]  ┌─────────── 176 ───────────┐    ┌──── 弹出面板 ────┐
 *  [配置]  │ [&lt;] 图标 标题 ………… ⓘ [▦] │ 4  │ 标题 ……… [×]   │
 *  [配置]  │ 页面（宽 162，x = 7）       │    │ 可滚动内容       │
 *          │ 玩家背包（x = 7，与页面对齐）│    └─────────────────┘
 *          └────────────────────────────┘
 *  ↑ 机器小组件（GTM 配置按钮）：窗口左侧一列，与窗口顶部内边距对齐，放不下才向左加列
 * </pre>
 *
 * <ul>
 * <li>外框 {@link UITheme#WINDOW}；标题栏在窗口内第一行，图标 + 标题 + 悬浮说明图标，历史非空时显示返回键。</li>
 * <li>页面不再居中：宽度不足 {@link UISizes#CONTENT_WIDTH} 时才在内容区内居中，否则左边缘固定在 x = 7。</li>
 * <li>玩家背包改用 Ore 槽位，与页面同一左边缘。</li>
 * <li>页面标签（导航）在窗口顶上横排（{@link WindowTabBar}），机器小组件（开关类）独占左侧——两类东西分开放，不再挤在同一侧。</li>
 * <li>右侧弹出面板见 {@link #registerPopup}：由页面注册，按键打开，每个打开界面的玩家各自独立。</li>
 * </ul>
 * 页面仍通过 {@link IFancyUIProvider#createMainPage} 创建，GTM 的子页面（无线、优先级等）也能直接放进来。
 */
public class MachineWindow extends FancyMachineUIWidget {

    public static final String POPUP_CLOSE = "gtceu.uipro.popup.close";

    private static final ResourceLocation LOGO = GTCEu.id("textures/gui/uipro/gto_logo.png");

    private static final TreeMap<ResourceLocation, Function<MachineWindow, UIElement>> RIGHT_ACCESSORY_FACTORIES = new TreeMap<>();
    private final ArrayList<UIElement> rightAccessories = new ArrayList<>(RIGHT_ACCESSORY_FACTORIES.size());
    private boolean placingAccessories;
    private int mainPanelWidth = UISizes.WINDOW_WIDTH;

    private final WindowTitleBar title;
    private final WindowTabBar tabs;
    private final PopupHost popups;
    /** 左侧机器小组件（替代 GTM 的配置面板，见 {@link WindowConfiguratorPanel}）。 */
    private final WindowConfiguratorPanel configurators = new WindowConfiguratorPanel();
    /// 页内浮层（见 {@link PageOverlay}）：窗口最后画它们、盖住处的鼠标先交给它们
    private final List<PageOverlay> overlays = new ArrayList<>();
    @Nullable
    private IntFunction<Widget> titleContent;
    private final List<Supplier<Widget>> titleTools = new ArrayList<>(1);
    private boolean titleContentHasIcon;
    /// 页面右侧伸出的一列（如滚动条）宽度：玩家背包按去掉这一列后的宽度居中，与页面里的槽位对齐（切换页面时清零）
    private int inventoryGutter;
    /// 独立窗口（见 IMachineSubWindows）：标题栏"返回"回到这台机器的主界面
    @Nullable
    private MetaMachine backToMachine;
    /// 始终按屏幕居中（见 setCentered）
    private boolean centered;
    private final boolean windowLogo;
    private boolean titleFollowsTab;
    private boolean placing;
    /** 客户端：第一次摆放时的窗口宽度，之后切页时窗口左边缘按它固定（见 {@link #applyClientPlacement}）。 */
    private int anchorWidth;
    /** 客户端：窗口顶边固定的屏幕纵坐标（第一次摆放时居中得出），以及当时的屏幕尺寸（变了就重新居中）。 */
    private int anchorTop = Integer.MIN_VALUE;
    private int anchorScreenWidth, anchorScreenHeight;
    @Nullable
    private IFancyUIProvider transientPage;
    private final List<IFancyUIProvider> transientPages = new ArrayList<>(0);
    private NavState nav = NavState.MAIN;
    private final Navigator navigator = new Navigator();
    private final UIStructure<NavState> navigation;
    private final RPC<Unit> backToMachineRequest;

    private record NavEntry(int home, int tab) {}

    private record NavState(int home, int tab, List<NavEntry> back, int transientPage) {

        private static final NavState MAIN = new NavState(0, 0, List.of(), -1);
    }

    private static final int SWITCHER = -1;
    private static final int MAX_HISTORY = 32;
    private static final int MAX_TAB = 255;
    private static final ByteStreamCodec<NavEntry> NAV_ENTRY = ByteStreamCodec.composite(
            ByteStreamCodec.INT_CODEC, NavEntry::home,
            ByteStreamCodec.INT_CODEC, NavEntry::tab,
            NavEntry::new);
    private static final ByteStreamCodec<NavState> NAV_STATE = ByteStreamCodec.composite(
            ByteStreamCodec.INT_CODEC, NavState::home,
            ByteStreamCodec.INT_CODEC, NavState::tab,
            UICodecs.list(NAV_ENTRY, MAX_HISTORY), NavState::back,
            ByteStreamCodec.INT_CODEC, NavState::transientPage,
            NavState::new);
    @Nullable
    private final ResourceLocation skin;

    public MachineWindow(IFancyUIProvider mainPage) {
        this(mainPage, () -> {});
    }

    public static ModularUI createUI(IFancyUIProvider mainPage, IUIHolder holder, Player player) {
        return new ModularUI(UISizes.WINDOW_WIDTH, UISizes.WINDOW_WIDTH, holder, player).widget(new MachineWindow(mainPage));
    }

    public MachineWindow(IFancyUIProvider mainPage, Runnable init) {
        super(mainPage, UISizes.WINDOW_WIDTH, UISizes.WINDOW_WIDTH, init);
        this.centered = mainPage.windowAnchor() == WindowAnchor.CENTER;
        this.windowLogo = mainPage.showsWindowLogo();
        this.skin = mainPage.getWindowSkin();
        setBackground(UITheme.WINDOW);
        // 标题栏、悬浮说明、页面标签由本类自绘；GTM 的三个控件留作数据容器（标签列表、选中项、导航回调），不进控件树
        removeWidget(titleBar);
        removeWidget(tooltipsPanel);
        removeWidget(sideTabsWidget);
        // 左侧机器小组件换成本框架的面板（移动用本框架的动画）；GTM 的面板摘出控件树，只剩字段
        removeWidget(configuratorPanel);
        configurators.setTexture(UITheme.CONFIGURATOR_TAB);
        addWidget(0, navigator);
        navigation = navigator.addStructure(NAV_STATE, () -> nav).validate(this::isValidNavigation)
                .apply(this::applyNavigation).onRejected(this::restoreTabHighlight);
        backToMachineRequest = navigator.addRPC(player -> {
            if (backToMachine != null && player instanceof ServerPlayer serverPlayer) MachineSubWindowFactory.openMachine(serverPlayer, backToMachine);
        });
        if (playerInventory != null) layoutPlayerInventory(playerInventory);
        addWidget(title = new WindowTitleBar());
        addWidget(tabs = new WindowTabBar());
        addWidget(popups = new PopupHost());
        for (var factory : RIGHT_ACCESSORY_FACTORIES.values()) {
            var accessory = Objects.requireNonNull(factory.apply(this));
            rightAccessories.add(accessory);
            addWidget(accessory);
        }
        // 左侧机器小组件最后加：展开后会伸到主窗口上方，要最先接到点击、最后绘制（另见 drawWidgetsBackground）
        addWidget(configurators);
    }

    /** GTM 的盖板配置等通过它打开浮动标签页，交给本框架的面板。 */
    @Override
    public ConfiguratorPanel getConfiguratorPanel() {
        return configurators;
    }

    /**
     * Registers an addon accessory in a separate panel connected to the main window's right edge. Call during common
     * setup, before opening any UI.
     * Factories run on both sides and must always create the same widget tree; hide unsupported accessories instead of
     * conditionally creating them. Visible accessories keep their declared width and stretch to the window's inner
     * height.
     * Accessory IDs determine the order on both sides, independently of addon initialization order.
     * After changing visibility, call {@link #onContentResized(Widget)} with the accessory to update the reserved
     * panel space.
     */
    public static void registerRightAccessory(ResourceLocation id, Function<MachineWindow, UIElement> factory) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(factory);
        if (RIGHT_ACCESSORY_FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate window accessory: " + id);
        }
    }

    // ==================== 展开的机器小组件盖住主窗口 ====================
    //
    // 展开的配置项（电路设置、共享库……）在屏幕窄时会伸到主窗口上方。它本身抬高了绘制层（WindowConfiguratorPanel），
    // 这里再让被它盖住的地方对主窗口的其他部分"不存在"：绘制时把鼠标挪到界外（不高亮、不弹提示），
    // 悬停、滚轮、EMI 查询只交给配置项；点击本来就先到配置项（它是最后加入的子控件），被它吃掉。

    /// 被盖住时交给其他部分的鼠标坐标：离开任何控件
    private static final int OUTSIDE = -100000;

    /**
     * 屏幕点击分发之前（{@code UIClientEvents}）：界面里每个窗口的显示中的页内浮层，这次点击不落在它上面的
     * （包括被展开的机器小组件盖住、或点在窗口外），通知它 {@link PageOverlay#onOutsideClick}。
     */
    @OnlyIn(Dist.CLIENT)
    public static void beforeGuiClick(WidgetGroup root, double mouseX, double mouseY) {
        for (var widget : root.widgets) {
            if (widget instanceof MachineWindow window) window.notifyOutsideClick(mouseX, mouseY);
            else if (widget instanceof WidgetGroup group) beforeGuiClick(group, mouseX, mouseY);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void notifyOutsideClick(double mouseX, double mouseY) {
        if (overlays.isEmpty()) return;
        var cover = coverAt(mouseX, mouseY);
        // 回调里可能关掉浮层、改动列表，先拷一份
        for (var overlay : List.copyOf(overlays)) {
            if (overlay != cover && overlay.isShown()) overlay.onOutsideClick();
        }
    }

    /** 页内浮层在 initWidget 时登记（两端都会调，只有客户端用）。 */
    void registerOverlay(PageOverlay overlay) {
        if (!overlays.contains(overlay)) overlays.add(overlay);
    }

    /** 鼠标下显示中的页内浮层（后登记的在上）；换页后已不在本窗口里的顺手清掉。 */
    @Nullable
    private PageOverlay coveringOverlay(double mouseX, double mouseY) {
        overlays.removeIf(overlay -> MachineWindow.of(overlay) != this);
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i).isCovering(mouseX, mouseY)) return overlays.get(i);
        }
        return null;
    }

    /** 这一点被谁盖住：展开的机器小组件优先，其次页内浮层；都没有为 null。 */
    @Nullable
    private Widget coverAt(double mouseX, double mouseY) {
        if (configurators.isCovering(mouseX, mouseY)) return configurators;
        return coveringOverlay(mouseX, mouseY);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawLogo(GuiGraphics graphics) {
        if (!windowLogo || playerInventory == null || !playerInventory.isVisible()) return;
        int x = playerInventory.getPositionX() + UISizes.SLOT_ROW_WIDTH - UISizes.LOGO_WIDTH;
        int y = playerInventory.getPositionY() - UISizes.LOGO_HEIGHT - UISizes.LOGO_GAP;
        RenderSystem.enableBlend();
        graphics.blit(LOGO, x, y, 0, 0, UISizes.LOGO_WIDTH, UISizes.LOGO_HEIGHT, UISizes.LOGO_WIDTH, UISizes.LOGO_HEIGHT);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (skin == null) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        ThemeSkin.of(skin).apply();
        try {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        } finally {
            ThemeSkin.getGlobal().apply();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (skin == null) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        ThemeSkin.of(skin).apply();
        try {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        } finally {
            ThemeSkin.getGlobal().apply();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawBackgroundTexture(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = getPosition().x, y = getPosition().y, height = getSizeHeight();
        int previousRight = x + mainPanelWidth;
        UITheme.WINDOW.draw(graphics, mouseX, mouseY, x, y, mainPanelWidth, height);
        for (var accessory : rightAccessories) {
            if (!accessory.isVisible()) continue;
            int panelX = accessory.getPositionX() - UISizes.WINDOW_PADDING_X;
            int panelWidth = accessory.getSizeWidth() + 2 * UISizes.WINDOW_PADDING_X;
            int gap = panelX - previousRight;
            UITheme.drawWindowConnector(graphics, previousRight, y + height / 6, gap);
            UITheme.drawWindowConnector(graphics, previousRight, y + height * 5 / 6, gap);
            UITheme.WINDOW.draw(graphics, mouseX, mouseY, panelX, y, panelWidth, height);
            previousRight = panelX + panelWidth;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawWidgetsBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawLogo(graphics);
        var cover = coverAt(mouseX, mouseY);
        if (cover == null) {
            super.drawWidgetsBackground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        for (var widget : widgets) {
            if (!widget.isVisible()) continue;
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableBlend();
            int x = widget == cover ? mouseX : OUTSIDE, y = widget == cover ? mouseY : OUTSIDE;
            if (widget.inAnimate()) widget.getAnimation().drawInBackground(graphics, x, y, partialTicks);
            else widget.drawInBackground(graphics, x, y, partialTicks);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawWidgetsForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var cover = coverAt(mouseX, mouseY);
        if (cover == null) {
            super.drawWidgetsForeground(graphics, mouseX, mouseY, partialTicks);
        } else {
            for (var widget : widgets) {
                if (!widget.isVisible()) continue;
                RenderSystem.setShaderColor(1, 1, 1, 1);
                int x = widget == cover ? mouseX : OUTSIDE, y = widget == cover ? mouseY : OUTSIDE;
                if (widget.inAnimate()) widget.getAnimation().drawInForeground(graphics, x, y, partialTicks);
                else widget.drawInForeground(graphics, x, y, partialTicks);
            }
        }
        // 页内浮层最后画（背景 + 前景），盖住所有格子；被展开的机器小组件盖住的地方不给它真实鼠标
        boolean configuratorsCover = cover == configurators;
        for (var overlay : overlays) {
            if (!overlay.isShown()) continue;
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableBlend();
            overlay.drawAsOverlay(graphics, configuratorsCover ? OUTSIDE : mouseX, configuratorsCover ? OUTSIDE : mouseY, partialTicks);
        }
    }

    @Override
    public @Nullable Widget getHoverElement(double mouseX, double mouseY) {
        var cover = coverAt(mouseX, mouseY);
        if (cover != null) return cover.getHoverElement(mouseX, mouseY);
        return super.getHoverElement(mouseX, mouseY);
    }

    /// 页内浮层盖住的地方点击只交给浮层（展开的机器小组件是最后加入的子控件，本来就先拿到点击）
    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!configurators.isCovering(mouseX, mouseY)) {
            var overlay = coveringOverlay(mouseX, mouseY);
            if (overlay != null) {
                overlay.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        var cover = coverAt(mouseX, mouseY);
        if (cover != null) {
            cover.mouseWheelMove(mouseX, mouseY, wheelDelta);
            return true;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        var cover = coverAt(mouseX, mouseY);
        if (cover != null) return cover instanceof WidgetGroup group ? group.getXEIIngredientOverMouse(mouseX, mouseY) : null;
        return super.getXEIIngredientOverMouse(mouseX, mouseY);
    }

    /** 从任意子控件向上找到所在的窗口；不在 {@link MachineWindow} 里时返回 null。 */
    @Nullable
    public static MachineWindow of(Widget widget) {
        for (Widget current = widget; current != null; current = current.getParent()) {
            if (current instanceof MachineWindow window) return window;
        }
        return null;
    }

    // ==================== 弹出面板 ====================

    /**
     * 注册一种弹出面板，由页面自己决定注册几种（可以不注册）。页面在 {@link IFancyUIProvider#createMainPage} 里调用
     * （两端都会执行），切换页面时注册表与已打开的面板一并清空。
     * <p>
     * 每种面板同时至多打开一个，{@code factory} 的参数是打开时传入的整数（如槽号），参数不合法时返回 null 拒绝打开；
     * 不同种的面板可以同时打开，在窗口右侧按打开顺序排列。
     */
    public void registerPopup(String key, IntFunction<Popup> factory) {
        popups.register(key, factory);
    }

    /** 打开弹出面板；客户端调用时请求服务端打开，两端随后一起构建。同一种面板已打开时原地替换成新参数。 */
    public void openPopup(String key, int argument) {
        popups.open(key, argument);
    }

    /** 以同样参数打开着时关闭，否则打开（或替换成这个参数）。 */
    public void togglePopup(String key, int argument) {
        if (isPopupOpen(key, argument)) closePopup(key);
        else openPopup(key, argument);
    }

    public void closePopup(String key) {
        popups.close(key);
    }

    public void closeAllPopups() {
        popups.closeAll();
    }

    /** 这种面板是否以该参数打开着（客户端可直接查询，用于高亮对应的槽等）。 */
    public boolean isPopupOpen(String key, int argument) {
        return popups.isOpen(key, argument);
    }

    public boolean isPopupOpen(String key) {
        return popups.isOpen(key);
    }

    /** 这种面板打开时的参数，没打开时返回 {@code -1}。 */
    public int getPopupArgument(String key) {
        return popups.argumentOf(key);
    }

    /** 界面里的机器窗口；界面不是用 {@link MachineWindow} 搭的时返回 null。 */
    @Nullable
    public static MachineWindow find(ModularUI ui) {
        for (var widget : ui.mainGroup.getContainedWidgets(true)) {
            if (widget instanceof MachineWindow window) return window;
        }
        return null;
    }

    // ==================== 独立窗口 ====================

    /**
     * 本窗口是机器的独立窗口（{@code IMachineSubWindows}）：没有可退回的页面时，标题栏左侧也显示"返回"按钮，
     * 点击后（服务端）回到这台机器的主界面。建界面时两端都要调用。
     */
    public MachineWindow setBackToMachine(MetaMachine machine) {
        this.backToMachine = machine;
        return this;
    }

    /**
     * 窗口（连同顶部标签栏）始终按屏幕正中摆放：页面尺寸变了（如画布拖拽缩放）就用动画回到正中，
     * 而不是像普通机器窗口那样固定左边缘和顶边、只向右下长。尺寸随屏幕撑大的大页面（如科技树）用它。
     * 这样的窗口拖拽缩放的上限是整个屏幕（留出边距），不是屏幕的 2/3。
     */
    public MachineWindow setCentered(boolean centered) {
        this.centered = centered;
        return this;
    }

    public boolean isCentered() {
        return centered;
    }

    public MachineWindow setTitleFollowsTab(boolean follows) {
        this.titleFollowsTab = follows;
        return this;
    }

    /** 独立窗口：Esc 和背包键回到机器主界面，而不是关掉界面（界面里的输入框等先处理按键）。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && isOnTransientPage()) {
            requestLeaveTransientPage();
            return true;
        }
        if (backToMachine != null && (keyCode == GLFW.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode))) {
            backToMachineRequest.send(Unit.INSTANCE);
            return true;
        }
        return false;
    }

    public void selectTab(IFancyUIProvider tab) {
        var list = getTabs();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == tab) {
                requestTab(i);
                return;
            }
        }
    }

    /** 标签栏里当前的页面（主页在前，子页面依次在后）。 */
    public List<IFancyUIProvider> getTabs() {
        var list = new ArrayList<IFancyUIProvider>(1 + sideTabsWidget.getSubTabs().size());
        if (sideTabsWidget.getMainTab() != null) list.add(sideTabsWidget.getMainTab());
        list.addAll(sideTabsWidget.getSubTabs());
        return list;
    }

    public int registerTransientPage(IFancyUIProvider page) {
        transientPages.add(page);
        return transientPages.size() - 1;
    }

    public void openTransientPage(int key) {
        var target = navigation.getTarget();
        if (target.transientPage() >= 0 || target.home() == SWITCHER || key < 0 || key >= transientPages.size()) return;
        navigation.request(new NavState(target.home(), target.tab(), target.back(), key));
    }

    public boolean isOnTransientPage() {
        return nav.transientPage() >= 0;
    }

    public void requestLeaveTransientPage() {
        var target = navigation.getTarget();
        if (target.transientPage() < 0) return;
        navigation.request(new NavState(target.home(), target.tab(), target.back(), -1));
    }

    public void requestBack() {
        var target = navigation.getTarget();
        if (!target.back().isEmpty()) {
            var back = new ArrayList<>(target.back());
            var entry = back.remove(back.size() - 1);
            navigation.request(new NavState(entry.home(), entry.tab(), List.copyOf(back), -1));
        } else if (backToMachine != null) {
            backToMachineRequest.send(Unit.INSTANCE);
        }
    }

    public void requestPageSwitcher() {
        var target = navigation.getTarget();
        if (target.home() == SWITCHER) return;
        var back = new ArrayList<>(target.back());
        if (target.tab() != 0 && !back.isEmpty()) back.remove(back.size() - 1);
        back.add(new NavEntry(target.home(), 0));
        navigation.request(new NavState(SWITCHER, 0, trimHistory(back), -1));
    }

    private void requestTab(int index) {
        var target = navigation.getTarget();
        if (target.home() != nav.home() || target.home() == SWITCHER) return;
        if (index == target.tab() && target.transientPage() < 0) return;
        var back = new ArrayList<>(target.back());
        if (target.home() == 0 && index == 0) {
            back.clear();
        } else if (!back.isEmpty() && back.get(back.size() - 1).equals(new NavEntry(target.home(), index))) {
            back.remove(back.size() - 1);
        } else if (index != target.tab()) {
            back.add(new NavEntry(target.home(), target.tab()));
        }
        navigation.request(new NavState(target.home(), index, trimHistory(back), -1));
    }

    private void requestHome(int home) {
        navigation.request(new NavState(home, 0, home == 0 ? List.of() : List.of(new NavEntry(0, 0)), -1));
    }

    private static List<NavEntry> trimHistory(List<NavEntry> back) {
        return List.copyOf(back.size() > MAX_HISTORY ? back.subList(back.size() - MAX_HISTORY, back.size()) : back);
    }

    @Override
    protected void navigate(IFancyUIProvider newPage) {
        selectTab(newPage);
    }

    @Override
    protected void navigate(IFancyUIProvider nextPage, IFancyUIProvider nextHomePage) {
        if (nextHomePage == currentHomePage) selectTab(nextPage);
    }

    @Override
    protected void navigateBack(ClickData clickData) {
        requestBack();
    }

    @Override
    protected void openPageSwitcher(ClickData clickData) {
        requestPageSwitcher();
    }

    @Override
    protected void switchPage(IFancyUIProvider nextHomePage) {
        int home = allPages == null ? -1 : allPages.indexOf(nextHomePage);
        if (home >= 0) requestHome(home);
    }

    private boolean isValidNavigation(NavState state) {
        int homes = allPages == null ? 1 : allPages.size();
        if (state.home() < SWITCHER || state.home() >= homes) return false;
        if (state.home() == SWITCHER ? state.tab() != 0 : state.tab() < 0 || state.tab() > MAX_TAB) return false;
        if (state.home() == nav.home() && state.home() != SWITCHER && state.tab() > sideTabsWidget.getSubTabs().size()) return false;
        for (var entry : state.back()) {
            if (entry.home() < 0 || entry.home() >= homes || entry.tab() < 0 || entry.tab() > MAX_TAB) return false;
        }
        return state.transientPage() < 0 || state.home() != SWITCHER && state.transientPage() < transientPages.size();
    }

    private void applyNavigation(NavState state) {
        var transientTarget = state.transientPage() >= 0 && state.transientPage() < transientPages.size() ? transientPages.get(state.transientPage()) : null;
        var previousTab = sideTabsWidget.getSelectedTab();
        var previousHome = currentHomePage;
        nav = state;
        boolean switcher = state.home() == SWITCHER || allPages == null;
        sideTabsWidget.setVisible(!switcher);
        sideTabsWidget.setActive(!switcher);
        IFancyUIProvider home;
        IFancyUIProvider page;
        if (switcher) {
            pageSwitcher.setPageList(allPages == null ? List.of(mainPage) : allPages, previousHome == null ? mainPage : previousHome);
            home = pageSwitcher;
            page = pageSwitcher;
        } else {
            home = allPages.get(state.home());
            if (previousHome != home) setupSideTabs(home);
            page = tabAt(home, state.tab());
            var onSwitch = sideTabsWidget.getOnTabSwitch();
            if (onSwitch != null && previousHome == home && previousTab != null && previousTab != page) onSwitch.accept(previousTab, page);
        }
        currentHomePage = home;
        transientPage = transientTarget;
        currentPage = transientTarget != null ? transientTarget : page;
        setupFancyUI(currentPage, currentPage.hasPlayerInventory());
    }

    private IFancyUIProvider tabAt(IFancyUIProvider home, int index) {
        var subTabs = sideTabsWidget.getSubTabs();
        if (index > 0 && index <= subTabs.size()) return subTabs.get(index - 1);
        var main = sideTabsWidget.getMainTab();
        return main == null ? home : main;
    }

    private IFancyUIProvider underlyingPage() {
        return nav.home() == SWITCHER || allPages == null ? pageSwitcher : tabAt(currentHomePage, nav.tab());
    }

    private void restoreTabHighlight() {
        if (nav.home() != SWITCHER) sideTabsWidget.selectTab(underlyingPage());
    }

    // ==================== 标题栏内容 ====================

    /**
     * 由页面自定义标题栏中段（图标与右侧说明图标之间），替代默认的页面标题文字；参数是中段可用宽度，
     * 返回的元素高度不超过 {@link UISizes#CONTROL_HEIGHT}。页面在 {@link IFancyUIProvider#createMainPage} 里设置
     * （两端都会执行），切换页面时清空。设置后页面标题改为鼠标停在图标上时显示。
     */
    public void setTitleContent(IntFunction<Widget> content) {
        this.titleContent = content;
        this.titleContentHasIcon = false;
    }

    @OnlyIn(Dist.CLIENT)
    public static int clientPageHeightLimit(boolean inventory) {
        int screen = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int usable = screen - clientVerticalReserve();
        int chrome = UISizes.WINDOW_PADDING_TOP + UISizes.CONTROL_HEIGHT + UISizes.SECTION_GAP + UISizes.WINDOW_PADDING_BOTTOM;
        if (inventory) chrome += UISizes.SECTION_GAP + UISizes.PLAYER_INVENTORY_HEIGHT;
        return Math.max(2 * UISizes.SLOT_SIZE, usable - chrome);
    }

    @OnlyIn(Dist.CLIENT)
    public int clientPageWidthLimit() {
        var area = ScreenArea.current(minWindowGroupWidth());
        int side = UISizes.SIDE_TAB_WIDTH + UISizes.GAP;
        int chrome = 2 * UISizes.WINDOW_PADDING_X + (centered ? side : 2 * side);
        return Math.max(UISizes.CONTENT_WIDTH, area.width() - chrome);
    }

    @OnlyIn(Dist.CLIENT)
    public int clientPageHeightLimitFor(boolean inventory) {
        int logoExtra = inventory ? inventoryGap() - UISizes.SECTION_GAP : 0;
        if (!centered) return Math.max(2 * UISizes.SLOT_SIZE, clientPageHeightLimit(inventory) - tabs.reservedHeight() - logoExtra);
        var area = ScreenArea.current(minWindowGroupWidth());
        int chrome = UISizes.WINDOW_PADDING_TOP + UISizes.CONTROL_HEIGHT + UISizes.SECTION_GAP + UISizes.WINDOW_PADDING_BOTTOM + tabs.reservedHeight();
        if (inventory) chrome += inventoryGap() + UISizes.PLAYER_INVENTORY_HEIGHT;
        return Math.max(2 * UISizes.SLOT_SIZE, area.height() - chrome);
    }

    @Override
    public int getPageHeightLimit() {
        return isRemote() ? clientPageHeightLimitFor(pageShowsInventory) : Integer.MAX_VALUE;
    }

    @OnlyIn(Dist.CLIENT)
    public static ScreenArea clientScreenArea() {
        return ScreenArea.current(minWindowGroupWidth());
    }

    private static int minWindowGroupWidth() {
        return UISizes.WINDOW_WIDTH + UISizes.SIDE_TAB_WIDTH + UISizes.GAP;
    }

    @OnlyIn(Dist.CLIENT)
    private static int clientBottomMargin() {
        int margin = UISizes.SCREEN_MARGIN;
        if (GTCEu.Mods.isEMILoaded()) margin += EmiCompat.centeredSearchHeight();
        return margin;
    }

    @OnlyIn(Dist.CLIENT)
    public static int clientVerticalReserve() {
        return UISizes.SCREEN_MARGIN + clientBottomMargin();
    }

    private static final class EmiCompat {

        private static final int SEARCH_HEIGHT = 18;

        private static int centeredSearchHeight() {
            return EmiConfig.enabled && EmiConfig.centerSearchBar ? SEARCH_HEIGHT : 0;
        }
    }

    public void addTitleTool(Supplier<Widget> tool) {
        titleTools.add(tool);
    }

    public void setTitleItem(Supplier<ItemStack> stack, Supplier<Component> name) {
        this.titleContent = width -> ItemTitle.of(width, stack, name);
        this.titleContentHasIcon = true;
    }

    /**
     * 页面右侧有一列伸出在槽位网格之外（常见是 9 槽宽的滚动区的滚动条，宽 {@code ScrollerView.SCROLL_BAR_SPACE}）：
     * 玩家背包按去掉这一列后的宽度居中，和页面里的槽位上下对齐，只有滚动条伸出来。
     * 页面在 {@link IFancyUIProvider#createMainPage} 里设置（两端都会执行），切换页面时清零。
     */
    public void setInventoryGutter(int rightGutter) {
        this.inventoryGutter = Math.max(0, rightGutter);
    }

    // ==================== 布局 ====================

    /** 切换页面（标签页、导航）：窗口从原位置用动画移到新位置（见 {@link #relayoutAnimated}）。 */
    @Override
    protected void setupFancyUI(IFancyUIProvider fancyUI, boolean showInventory) {
        relayoutAnimated(() -> setupPage(fancyUI, showInventory));
    }

    /**
     * 导航：一次建好目标页。GTM 进子页面前要先建一遍所在主页（为了主页的布局），新窗口的 {@link #setupPage} 本身就完整摆放，
     * 这一遍只会让两端各多建一次页，服务端还会多发一份主页的初始数据。
     */
    @Override
    protected void performNavigation(IFancyUIProvider nextPage, IFancyUIProvider nextHomePage) {
        applyNavigation(nav);
    }

    private void setupPage(IFancyUIProvider fancyUI, boolean showInventory) {
        clearUI();
        configurators.clear();
        popups.reset();
        titleContent = null;
        titleContentHasIcon = false;
        titleTools.clear();
        inventoryGutter = Math.max(0, fancyUI.getInventoryGutter());
        transientPages.clear();
        pageShowsInventory = showInventory;
        boolean onTransient = isOnTransientPage();
        sideTabsWidget.selectTab(onTransient ? underlyingPage() : fancyUI);
        var page = fancyUI.createMainPage(this);
        placePage(page, showInventory);
        pageContainer.addWidget(page);
        int contentWidth = placePage(page, showInventory);

        // 页面标签在窗口顶上横排；左侧只放机器小组件（配置按钮），与窗口顶部内边距对齐，一列放不下才向左加列
        tabs.setup();
        fancyUI.attachConfigurators(configurators);
        OutputSideConfigurator.attach(configurators, fancyUI);
        PartCellsConfigurator.attach(configurators, fancyUI);
        placeConfigurators();
        fancyUI.attachTooltips(tooltipsPanel);
        title.setup(titleFollowsTab || onTransient ? fancyUI : currentHomePage, contentWidth, !onTransient && (!nav.back().isEmpty() || backToMachine != null),
                !onTransient && allPages.size() > 1 && currentPage != pageSwitcher, onTransient, titleContent, titleContentHasIcon);

        updatePlacement();
    }

    /** 按页面尺寸摆放标题、页面、玩家背包并确定窗口尺寸，返回内容宽度。只改尺寸和位置，不增删控件。 */
    private int placePage(Widget page, boolean showInventory) {
        int contentWidth = Math.max(UISizes.CONTENT_WIDTH, page.getSizeWidth());
        int width = contentWidth + 2 * UISizes.WINDOW_PADDING_X;
        int y = UISizes.WINDOW_PADDING_TOP;
        title.setSelfPosition(new Position(UISizes.WINDOW_PADDING_X, y));
        y += UISizes.CONTROL_HEIGHT + UISizes.SECTION_GAP;

        page.setSelfPosition(new Position(UISizes.WINDOW_PADDING_X + (contentWidth - page.getSizeWidth()) / 2, y));
        y += page.getSizeHeight();

        boolean inventory = showInventory && playerInventory != null;
        if (playerInventory != null) {
            int pageX = UISizes.WINDOW_PADDING_X + (contentWidth - page.getSizeWidth()) / 2;
            int alignWidth = page.getSizeWidth() - inventoryGutter;
            int inventoryX = inventoryGutter > 0 && alignWidth >= UISizes.SLOT_ROW_WIDTH ? pageX + (alignWidth - UISizes.SLOT_ROW_WIDTH) / 2 :
                    UISizes.WINDOW_PADDING_X + (contentWidth - UISizes.SLOT_ROW_WIDTH) / 2;
            playerInventory.setSelfPosition(new Position(inventoryX, y + inventoryGap()));
            playerInventory.setActive(inventory);
            playerInventory.setVisible(inventory);
        }
        if (inventory) y += inventoryGap() + UISizes.PLAYER_INVENTORY_HEIGHT;
        int height = y + UISizes.WINDOW_PADDING_BOTTOM;

        int accessoryHeight = height - UISizes.WINDOW_PADDING_TOP - UISizes.WINDOW_PADDING_BOTTOM;
        for (var accessory : rightAccessories) {
            if (accessory.isVisible()) accessoryHeight = Math.max(accessoryHeight, accessory.getLayoutStyle().declaredMinHeight());
        }
        height = Math.max(height, accessoryHeight + UISizes.WINDOW_PADDING_TOP + UISizes.WINDOW_PADDING_BOTTOM);
        int gutterHeight = accessoryHeight;
        mainPanelWidth = width;
        int accessoryX = width;
        placingAccessories = true;
        {
            for (var accessory : rightAccessories) {
                if (!accessory.isVisible()) continue;
                accessory.layout(l -> l.height(gutterHeight));
                accessoryX += UISizes.SECTION_GAP;
                accessory.setSelfPosition(new Position(accessoryX + UISizes.WINDOW_PADDING_X, UISizes.WINDOW_PADDING_TOP));
                accessoryX += accessory.getSizeWidth() + 2 * UISizes.WINDOW_PADDING_X;
            }
        }
        placingAccessories = false;
        width = accessoryX;

        setSize(new Size(width, height));
        pageContainer.setSize(new Size(width, height));
        return contentWidth;
    }

    private int inventoryGap() {
        return windowLogo ? Math.max(UISizes.SECTION_GAP, UISizes.LOGO_HEIGHT + 2 * UISizes.LOGO_GAP) : UISizes.SECTION_GAP;
    }

    private void placeConfigurators() {
        configurators.setAvailableHeight(getSizeHeight() - UISizes.WINDOW_PADDING_TOP - UISizes.WINDOW_PADDING_BOTTOM);
        // GTM 只在挂配置项时按可用高度排版；可用高度变了必须重排（不传参数即只重排），否则每列个数、行位置沿用旧值：
        // 按钮排成参差的两列，收起展开项后还会按旧行数算出负坐标、跑到窗口上方
        configurators.attachConfigurators();
        configurators.setSelfPosition(new Position(-configurators.getSizeWidth() - UISizes.GAP, UISizes.WINDOW_PADDING_TOP));
    }

    /** 页面或弹出面板的尺寸变了（内容增减、滚动区拖拽缩放）：窗口随之重排，界面尺寸与面板位置重新摆放。 */
    @Override
    public void onContentResized(Widget root) {
        if (placingAccessories) return;
        if (root == popups) {
            relayoutAnimated(this::updatePlacement);
        } else if (rightAccessories.contains(root)) {
            if (!pageContainer.widgets.isEmpty()) onContentResized(pageContainer.widgets.get(0));
        } else if (root.getParent() == pageContainer && pageContainer.widgets.contains(root)) {
            relayoutAnimated(() -> {
                title.resize(placePage(root, pageShowsInventory));
                placeConfigurators();
                updatePlacement();
            });
        }
    }

    /**
     * 摆放弹出面板并确定界面尺寸。原则：打开面板不挪主窗口——界面尺寸就是主窗口尺寸，面板挂在窗口右侧界外
     * （LDLib 会把界外控件报给 EMI 避让）；只有面板超出屏幕右边时，才把主窗口左移刚好够的距离（不越过左侧悬浮栏）。
     * 面板默认与窗口顶边对齐，超出屏幕底边时自己上移。屏幕尺寸只在客户端知道，服务端只放默认位置（不影响同步）。
     * <p>
     * 注意：LDLib 的 {@code ModularUI.setSize} 只存在于客户端（专用服务器上被剥掉），服务端绝不能调用，
     * 所以界面尺寸只在 {@link #applyClientPlacement} 里设。
     */
    private void updatePlacement() {
        if (getGui() == null || placing) return;
        placing = true;
        try {
            if (isRemote()) applyClientPlacement();
            else popups.setSelfPosition(new Position(getSizeWidth() + UISizes.POPUP_GAP, 0));
        } finally {
            placing = false;
        }
    }

    /**
     * 客户端：窗口在屏幕上的位置固定，切页只改变窗口的高和宽，不重新居中。
     * <ul>
     * <li>水平：以第一次摆放时的窗口宽度为基准（{@link #anchorWidth}）按屏幕居中，左边缘固定；页面更宽时只向右长
     * （界面两侧各加宽 {@code extra}、窗口在界面里右移 {@code extra}）。</li>
     * <li>竖直：打开阶段（连同顶部标签栏）按屏幕居中，记下窗口顶边（{@link #anchorTop}），之后标签栏和窗口顶边都停在这里，
     * 页面变高只向下长。屏幕尺寸变了重新居中。</li>
     * </ul>
     * 界面（ModularUI）尺寸取能包住标签栏和窗口、且按屏幕居中后正好落在上述位置的大小——窗口始终完整在界面范围内
     * （界面外的点击会被原版当成点到界面外）。
     */
    @OnlyIn(Dist.CLIENT)
    private void applyClientPlacement() {
        var screen = Minecraft.getInstance().getWindow();
        int screenWidth = screen.getGuiScaledWidth(), screenHeight = screen.getGuiScaledHeight();
        int width = getSizeWidth(), height = getSizeHeight();
        int tabsHeight = tabs.reservedHeight();
        if (centered) {
            applyCenteredPlacement(screenWidth, screenHeight, width, height, tabsHeight);
            return;
        }
        if (anchorWidth <= 0 || centered) anchorWidth = width;
        // 打开阶段（首屏数据陆续到达、页面还在变高）一直按屏幕居中，之后才固定；始终居中的窗口每次都重新居中
        if (centered || anchorTop == Integer.MIN_VALUE || isOpening() || anchorScreenWidth != screenWidth || anchorScreenHeight != screenHeight) {
            anchorTop = (screenHeight - height - tabsHeight) / 2 + tabsHeight;
            anchorScreenWidth = screenWidth;
            anchorScreenHeight = screenHeight;
        }
        int bottomLimit = screenHeight - clientBottomMargin();
        int minTop = tabsHeight + UISizes.SCREEN_MARGIN;
        int top = centered ? Math.max(minTop, anchorTop) : Math.max(minTop, Math.min(anchorTop, bottomLimit - height));

        int extra = Math.max(0, width - anchorWidth);
        int[] placement = clientPlacement(screenWidth, screenHeight, width, top);
        // 界面按屏幕居中：高度取能让 [标签栏顶, 窗口底] 落在界面里的最小值
        int guiHeight = Math.max(height + tabsHeight, Math.max(screenHeight - 2 * (top - tabsHeight), 2 * (top + height) - screenHeight));
        while ((screenHeight - guiHeight) / 2 > top - tabsHeight || (screenHeight - guiHeight) / 2 + guiHeight < top + height) guiHeight++;
        getGui().setSize(anchorWidth + 2 * extra + 2 * placement[0], guiHeight);
        setWindowBasePosition(extra, top - (screenHeight - guiHeight) / 2);
        popups.setSelfPosition(new Position(width + UISizes.POPUP_GAP, placement[1]));
    }

    /** 客户端：{主窗口左移量, 面板相对窗口顶边的纵向偏移}，并按屏幕高度设定面板高度上限。{@code top} 是窗口顶边的屏幕坐标。 */
    @OnlyIn(Dist.CLIENT)
    private int[] clientPlacement(int screenWidth, int screenHeight, int width, int top) {
        int margin = UISizes.SCREEN_MARGIN;
        popups.setMaxHeight(Math.max(UISizes.SLOT_SIZE, screenHeight - 2 * margin));
        int popupWidth = popups.getSizeWidth(), popupHeight = popups.getSizeHeight();
        if (popupWidth == 0) return new int[] { 0, 0 };
        // 窗口左边缘固定在按基准宽度居中的位置（见 applyClientPlacement）
        int left = (screenWidth - anchorWidth) / 2;
        int overflow = left + width + UISizes.POPUP_GAP + popupWidth + margin - screenWidth;
        int reserved = configurators.getTabs().isEmpty() ? 0 : configurators.getSizeWidth() + UISizes.GAP;
        int maxShift = Math.max(0, left - reserved - margin);
        int shift = Math.max(0, Math.min(overflow, maxShift));
        int offsetY = Math.min(0, screenHeight - margin - top - popupHeight);
        offsetY = Math.max(offsetY, margin - top);
        return new int[] { shift, offsetY };
    }

    @OnlyIn(Dist.CLIENT)
    private void applyCenteredPlacement(int screenWidth, int screenHeight, int width, int height, int tabsHeight) {
        var area = ScreenArea.current(minWindowGroupWidth());
        anchorWidth = width;
        int reserved = configurators.getTabs().isEmpty() ? 0 : configurators.getSizeWidth() + UISizes.GAP;
        int left = area.left() + reserved + Math.max(0, (area.width() - reserved - width) / 2);
        int top = area.top() + tabsHeight + Math.max(0, (area.height() - tabsHeight - height) / 2);
        popups.setMaxHeight(Math.max(UISizes.SLOT_SIZE, area.height()));
        int popupWidth = popups.getSizeWidth(), popupHeight = popups.getSizeHeight();
        int popupY = 0;
        if (popupWidth > 0) {
            int overflow = left + width + UISizes.POPUP_GAP + popupWidth - area.right();
            int maxShift = Math.max(0, left - reserved - area.left());
            left -= Math.max(0, Math.min(overflow, maxShift));
            popupY = Math.max(area.top() - top, Math.min(0, area.bottom() - top - popupHeight));
        }
        int groupRight = left + width + (popupWidth > 0 ? UISizes.POPUP_GAP + popupWidth : 0);
        int halfWidth = Math.max(screenWidth / 2 - (left - reserved), groupRight - screenWidth / 2);
        int halfHeight = Math.max(screenHeight / 2 - (top - tabsHeight), top + height - screenHeight / 2);
        int guiWidth = Math.min(screenWidth, 2 * halfWidth), guiHeight = Math.min(screenHeight, 2 * halfHeight);
        getGui().setSize(guiWidth, guiHeight);
        setWindowBasePosition(left - (screenWidth - guiWidth) / 2, top - (screenHeight - guiHeight) / 2);
        popups.setSelfPosition(new Position(width + UISizes.POPUP_GAP, popupY));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected int maxWindowExtent(boolean vertical, int screen) {
        if (!centered) return super.maxWindowExtent(vertical, screen);
        var area = ScreenArea.current(minWindowGroupWidth());
        if (vertical) return area.height() - tabs.reservedHeight();
        return area.width() - (configurators.getTabs().isEmpty() ? 0 : configurators.getSizeWidth() + UISizes.GAP);
    }

    @Override
    public void endInteractiveResize() {
        if (isRemote()) {
            anchorTop = Integer.MIN_VALUE;
            updatePlacement();
        }
        super.endInteractiveResize();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void onScreenSizeUpdate(int screenWidth, int screenHeight) {
        super.onScreenSizeUpdate(screenWidth, screenHeight);
        updatePlacement();
    }

    /** 背包 3×9 + 快捷栏按 18 格紧排，快捷栏与背包隔 4，整体宽 162，槽位用 Ore 底图。 */
    private static void layoutPlayerInventory(PlayerInventoryWidget inventory) {
        int index = 0;
        for (var widget : inventory.getContainedWidgets(true)) {
            if (!(widget instanceof SlotWidget)) continue;
            // 前 9 个是快捷栏，之后按行排背包
            if (index < UISizes.SLOTS_PER_ROW) {
                widget.setSelfPosition(new Position(index * UISizes.SLOT_SIZE, 3 * UISizes.SLOT_SIZE + UISizes.SECTION_GAP));
            } else {
                int slot = index - UISizes.SLOTS_PER_ROW;
                widget.setSelfPosition(new Position(slot % UISizes.SLOTS_PER_ROW * UISizes.SLOT_SIZE, slot / UISizes.SLOTS_PER_ROW * UISizes.SLOT_SIZE));
            }
            index++;
        }
        inventory.setSize(new Size(UISizes.SLOT_ROW_WIDTH, UISizes.PLAYER_INVENTORY_HEIGHT));
        inventory.setSlotBackground(UITheme.ITEM_SLOT);
    }

    // ==================== 页面标签 ====================

    /**
     * 窗口顶上横排的页面标签，替代 GTM 左侧竖排的标签列（GTM 的 {@code sideTabsWidget} 只留作数据：标签列表、选中项、导航回调）。
     * 主页在最左，子页面依次往右；未选中的标签坐在窗口顶边上，选中的标签高出一截、向下伸进窗口顶边并抹掉接缝，与窗口连成一体。
     * 只有主页、或 GTM 隐藏了标签（打开"切换部件页"、单页界面）时不显示——这两种状态在切页过程中才变，所以绘制和点击时实时读取。
     */
    private final class WindowTabBar extends Widget {

        /// 标签之间的空隙
        private static final int TAB_GAP = 1;
        /// Ore 边框在左右两侧的可见宽度：第一个标签的内侧与页面左边缘对齐
        private static final int TAB_BORDER = 2;
        private static final int ICON = 16;
        private static final long HINT_PERIOD_MS = 1600;
        private static final int HINT_HEAD = 3;
        private static final int HINT_TAIL = 7;
        private static final int[] HINT_STEPS = { 1, 0, 0, 1, -1, 0, 0, -1, 1, 1, -1, 1, -1, -1, 1, -1 };

        private int tabWidth = UISizes.PAGE_TAB_WIDTH;
        private boolean[] hints = new boolean[0];
        private int hintWidth = -1, hintHeight = -1;
        private int[] hintLoopX = new int[0], hintLoopY = new int[0];
        private int[] hintInnerX = new int[0], hintInnerY = new int[0];

        private WindowTabBar() {
            super(0, 0, 0, 0);
        }

        /** 标签的位置与宽度只按标准窗口宽度算、与当前页面宽度无关：切页时标签栏左右不动，只随窗口上下移动。 */
        private void setup() {
            int count = count();
            int left = UISizes.WINDOW_PADDING_X - TAB_BORDER;
            int available = UISizes.WINDOW_WIDTH - 2 * left;
            tabWidth = Math.max(UISizes.PAGE_TAB_MIN_WIDTH, Math.min(UISizes.PAGE_TAB_WIDTH, (available + TAB_GAP) / count - TAB_GAP));
            int top = -UISizes.PAGE_TAB_HEIGHT - UISizes.PAGE_TAB_RAISE;
            setSelfPosition(new Position(left, top));
            setSize(new Size(count * (tabWidth + TAB_GAP) - TAB_GAP, -top + UISizes.PAGE_TAB_OVERLAP));
            setupHints(count);
        }

        private void setupHints(int count) {
            if (hints.length != count) hints = new boolean[count];
            var player = hintPlayer();
            var selected = sideTabsWidget.getSelectedTab();
            for (int i = 0; i < count; i++) {
                var tab = tab(i);
                hints[i] = player != null && tab instanceof IFirstVisitTab visit && !UIPreferences.hasVisitedTab(player, visit.getFirstVisitKey());
                if (hints[i] && tab == selected) markVisited(i);
            }
        }

        @Nullable
        private UUID hintPlayer() {
            return gui != null && gui.entityPlayer != null && gui.entityPlayer.level().isClientSide ? gui.entityPlayer.getUUID() : null;
        }

        private void markVisited(int index) {
            if (index < 0 || index >= hints.length || !hints[index]) return;
            hints[index] = false;
            var player = hintPlayer();
            if (player != null && tab(index) instanceof IFirstVisitTab visit) UIPreferences.markTabVisited(player, visit.getFirstVisitKey());
        }

        private void markVisited(IFancyUIProvider tab) {
            for (int i = 0, count = Math.min(count(), hints.length); i < count; i++) {
                if (tab(i) == tab) markVisited(i);
            }
        }

        /** 标签数：主页 + 子页面。 */
        private int count() {
            return 1 + sideTabsWidget.getSubTabs().size();
        }

        /** 第 {@code index} 个标签：0 是主页，之后是子页面。 */
        private IFancyUIProvider tab(int index) {
            return index == 0 ? sideTabsWidget.getMainTab() : sideTabsWidget.getSubTabs().get(index - 1);
        }

        /**
         * 界面为标签栏预留的高度：有子页面就预留，不看 GTM 此刻是否隐藏标签——隐藏状态在切页过程中才变，
         * 跟着它变会让窗口在打开/关闭"切换部件页"时上下跳。
         */
        private int reservedHeight() {
            return sideTabsWidget.getSubTabs().isEmpty() ? 0 : UISizes.PAGE_TAB_HEIGHT + UISizes.PAGE_TAB_RAISE;
        }

        private boolean shown() {
            return sideTabsWidget.isVisible() && !sideTabsWidget.getSubTabs().isEmpty() && sideTabsWidget.getMainTab() != null;
        }

        private int tabX(int index) {
            return getPositionX() + index * (tabWidth + TAB_GAP);
        }

        /** 窗口顶边的屏幕纵坐标。 */
        private int windowTop() {
            return MachineWindow.this.getPositionY();
        }

        @OnlyIn(Dist.CLIENT)
        private int hoveredIndex(double mouseX, double mouseY) {
            int top = windowTop() - UISizes.PAGE_TAB_HEIGHT - UISizes.PAGE_TAB_RAISE;
            int height = UISizes.PAGE_TAB_HEIGHT + UISizes.PAGE_TAB_RAISE;
            for (int i = 0, count = count(); i < count; i++) {
                if (isMouseOver(tabX(i), top, tabWidth, height, mouseX, mouseY)) return i;
            }
            return -1;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (!shown()) return;
            var selected = sideTabsWidget.getSelectedTab();
            int hovered = hoveredIndex(mouseX, mouseY);
            int base = windowTop();
            int selectedIndex = -1;
            for (int i = 0, count = count(); i < count; i++) {
                var tab = tab(i);
                if (tab == selected) {
                    selectedIndex = i;
                    continue;
                }
                int x = tabX(i), top = base - UISizes.PAGE_TAB_HEIGHT;
                UITheme.PAGE_TAB.draw(graphics, i == hovered ? UIStates.HOVERED : UIStates.NONE, x, top, tabWidth, UISizes.PAGE_TAB_HEIGHT);
                tab.getTabIcon().draw(graphics, mouseX, mouseY, UIPixels.center(x, tabWidth, ICON), top + TAB_BORDER, ICON, ICON);
            }
            if (selectedIndex < 0) return;
            int x = tabX(selectedIndex), top = base - UISizes.PAGE_TAB_HEIGHT - UISizes.PAGE_TAB_RAISE;
            int bottom = base + UISizes.PAGE_TAB_OVERLAP;
            UITheme.PAGE_TAB.draw(graphics, UIStates.SELECTED, x, top, tabWidth, bottom - top);
            tab(selectedIndex).getTabIcon().draw(graphics, mouseX, mouseY, UIPixels.center(x, tabWidth, ICON), top + TAB_BORDER + 1, ICON, ICON);
            drawHints(graphics, base, selectedIndex);
        }

        @OnlyIn(Dist.CLIENT)
        private void drawHints(GuiGraphics graphics, int base, int selectedIndex) {
            for (int i = 0, count = Math.min(count(), hints.length); i < count; i++) {
                if (hints[i] && i != selectedIndex) drawHint(graphics, tabX(i), base - UISizes.PAGE_TAB_HEIGHT, tabWidth, UISizes.PAGE_TAB_HEIGHT);
            }
        }

        @OnlyIn(Dist.CLIENT)
        private void drawHint(GuiGraphics graphics, int x, int y, int width, int height) {
            if (width != hintWidth || height != hintHeight) buildHintOutline(width, height);
            float phase = UIClock.pulse();
            int rgb = UITheme.TAB_HINT_GLOW & 0xFFFFFF;
            int ring = ColorMath.lerp(UITheme.TAB_HINT_DIM, UITheme.TAB_HINT_GLOW, phase);
            int halo = (int) (UITheme.TAB_HINT_HALO_ALPHA_MIN + (UITheme.TAB_HINT_HALO_ALPHA_MAX - UITheme.TAB_HINT_HALO_ALPHA_MIN) * phase) << 24 | rgb;
            int length = hintLoopX.length;
            int head = (int) (UIClock.phase(HINT_PERIOD_MS) * length);
            graphics.drawManaged(() -> {
                for (int i = 0; i < hintInnerX.length; i++) {
                    graphics.fill(x + hintInnerX[i], y + hintInnerY[i], x + hintInnerX[i] + 1, y + hintInnerY[i] + 1, halo);
                }
                for (int i = 0; i < length; i++) {
                    int d = Math.floorMod(head - i, length);
                    int color;
                    if (d < HINT_HEAD) color = UITheme.TAB_HINT_SPARK;
                    else if (d < HINT_HEAD + HINT_TAIL) color = ColorMath.lerp(UITheme.TAB_HINT_GLOW, ring, (d - HINT_HEAD) / (float) HINT_TAIL);
                    else color = ring;
                    graphics.fill(x + hintLoopX[i], y + hintLoopY[i], x + hintLoopX[i] + 1, y + hintLoopY[i] + 1, color);
                }
            });
        }

        private void buildHintOutline(int width, int height) {
            hintWidth = width;
            hintHeight = height;
            boolean[] outline = new boolean[width * height];
            int count = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (tabOpaque(x, y, width, height) && (!tabOpaque(x - 1, y, width, height) || !tabOpaque(x + 1, y, width, height) ||
                            !tabOpaque(x, y - 1, width, height) || !tabOpaque(x, y + 1, width, height))) {
                        outline[y * width + x] = true;
                        count++;
                    }
                }
            }
            int[] loopX = new int[count], loopY = new int[count];
            boolean[] visited = new boolean[width * height];
            int cx = 2, cy = 0, size = 0;
            while (size < count) {
                visited[cy * width + cx] = true;
                loopX[size] = cx;
                loopY[size] = cy;
                size++;
                boolean moved = false;
                for (int k = 0; k < HINT_STEPS.length && !moved; k += 2) {
                    int nx = cx + HINT_STEPS[k], ny = cy + HINT_STEPS[k + 1];
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int index = ny * width + nx;
                    if (outline[index] && !visited[index]) {
                        cx = nx;
                        cy = ny;
                        moved = true;
                    }
                }
                if (!moved) break;
            }
            hintLoopX = size == count ? loopX : Arrays.copyOf(loopX, size);
            hintLoopY = size == count ? loopY : Arrays.copyOf(loopY, size);
            int inner = 0;
            int[] innerX = new int[width * height], innerY = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    if (outline[index] || !tabOpaque(x, y, width, height)) continue;
                    if ((x > 0 && outline[index - 1]) || (x + 1 < width && outline[index + 1]) ||
                            (y > 0 && outline[index - width]) || (y + 1 < height && outline[index + width])) {
                        innerX[inner] = x;
                        innerY[inner] = y;
                        inner++;
                    }
                }
            }
            hintInnerX = Arrays.copyOf(innerX, inner);
            hintInnerY = Arrays.copyOf(innerY, inner);
        }

        private static boolean tabOpaque(int x, int y, int width, int height) {
            if (x < 0 || y < 0 || x >= width || y >= height) return false;
            if (y == 0) return x >= 2 && x <= width - 4;
            if (y == 1) return x >= 2 && x <= width - 3;
            if (y == 2 || y == height - 3) return x >= 1 && x <= width - 2;
            if (y == height - 2) return x >= 2 && x <= width - 3;
            if (y == height - 1) return x >= 3 && x <= width - 4;
            return true;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (!shown() || gui == null || gui.getModularUIGui() == null) return;
            int hovered = hoveredIndex(mouseX, mouseY);
            if (hovered < 0) return;
            var tab = tab(hovered);
            var callout = tab.getTabCallout();
            if (callout == null) {
                gui.getModularUIGui().setHoverTooltip(tab.getTabTooltips(), ItemStack.EMPTY, null, tab.getTabTooltipComponent());
                return;
            }
            int centerX = tabX(hovered) + tabWidth / 2;
            int top = windowTop() + UISizes.PAGE_TAB_OVERLAP + CalloutBubble.NOTCH;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(0, 0, UILayers.TOP);
            CalloutBubble.drawGlyph(graphics, centerX - CalloutBubble.GLYPH_WIDTH / 2, top, centerX, CalloutBubble.Tone.NEUTRAL, callout);
            pose.popPose();
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!shown() || button != 0) return false;
            int index = hoveredIndex(mouseX, mouseY);
            if (index < 0) return false;
            var tab = tab(index);
            if (tab != sideTabsWidget.getSelectedTab() || isOnTransientPage()) {
                requestTab(index);
                sideTabsWidget.selectTab(tab);
                playButtonClickSound();
            }
            return true;
        }
    }

    // ==================== 标题栏 ====================

    /**
     * 窗口内第一行：{@code [<]} 返回（有历史时）、页面图标、中段（默认是页面标题，页面可用 {@link #setTitleContent} 换成自己的控件）、
     * 右侧 GTM 悬浮说明图标（{@link IFancyTooltip}，12 见方，悬停显示说明；按已挂上的个数预留位置）、{@code [▦]}
     * 页面按钮（{@link UITheme#PAGES}，打开页面切换页，多于一页时）。
     * 两端在每次切换页面时一起重建子控件，控件树保持一致。
     */
    private final class WindowTitleBar extends WidgetGroup {

        /// 页面图标、说明图标都与控件同高
        private static final int ICON = UISizes.CONTROL_HEIGHT;

        @Nullable
        private IFancyUIProvider page;
        private boolean customContent;
        private int iconLeft;
        private int textLeft;
        private int textRight;
        private int tooltipsRight;
        @Nullable
        private Widget rightButton;
        private final List<Widget> rightTools = new ArrayList<>(1);
        @Nullable
        private Widget contentWidget;

        private WindowTitleBar() {
            super(0, 0, UISizes.CONTENT_WIDTH, UISizes.CONTROL_HEIGHT);
        }

        private void setup(IFancyUIProvider page, int width, boolean showBack, boolean showMenu, boolean showClose, @Nullable IntFunction<Widget> content, boolean contentHasIcon) {
            this.page = page;
            clearAllWidgets();
            rightButton = null;
            rightTools.clear();
            contentWidget = null;
            setSize(new Size(width, UISizes.CONTROL_HEIGHT));
            int left = 0;
            int right = width;
            if (showBack) {
                var back = Button.icon(UITheme.ARROW_LEFT).setOnClientClick(MachineWindow.this::requestBack);
                back.tooltips("gtceu.gui.title_bar.back");
                addWidget(back);
                left += UISizes.ICON_BUTTON_SIZE + UISizes.GAP;
            }
            if (showMenu) {
                right -= UISizes.ICON_BUTTON_SIZE;
                var menu = Button.icon(UITheme.PAGES).setOnClientClick(MachineWindow.this::requestPageSwitcher);
                menu.tooltips("gtceu.gui.title_bar.page_switcher");
                menu.setSelfPosition(new Position(right, 0));
                addWidget(menu);
                rightButton = menu;
                right -= UISizes.GAP;
            }
            if (showClose) {
                right -= UISizes.ICON_BUTTON_SIZE;
                var close = Button.glyph("×").setOnClientClick(MachineWindow.this::requestLeaveTransientPage);
                close.tooltips(POPUP_CLOSE);
                close.setSelfPosition(new Position(right, 0));
                addWidget(close);
                rightButton = close;
                right -= UISizes.GAP;
            }
            for (var supplier : titleTools) {
                var tool = supplier.get();
                right -= tool.getSizeWidth();
                tool.setSelfPosition(new Position(right, (UISizes.CONTROL_HEIGHT - tool.getSizeHeight()) / 2));
                addWidget(tool);
                rightTools.add(tool);
                right -= UISizes.GAP;
            }
            iconLeft = left;
            if (content != null && contentHasIcon) {
                textLeft = left;
            } else {
                var icon = ItemView.of(page.getTabIcon());
                icon.tooltips(page.getTitle());
                icon.setSelfPosition(new Position(left, 0));
                addWidget(icon);
                textLeft = left + ICON + UISizes.GAP;
            }
            tooltipsRight = right;
            // 自定义中段按建页时实际显示的说明图标预留（默认标题在绘制时按实时显示的个数让位）
            int shown = 0;
            for (int i = 0, n = tooltipsPanel.getTooltips().size(); i < n; i++) if (tooltipsPanel.isShown(i)) shown++;
            textRight = right - shown * (ICON + UISizes.GAP);
            customContent = content != null;
            if (content != null) {
                var widget = content.apply(Math.max(0, textRight - textLeft));
                widget.setSelfPosition(new Position(textLeft, (UISizes.CONTROL_HEIGHT - widget.getSizeHeight()) / 2));
                addWidget(widget);
                contentWidget = widget;
            }
        }

        /** 窗口宽度变了（页面内容变宽 / 变窄）：右侧按钮与说明图标跟着移动，自定义中段（UIElement）改宽度；不增删控件。 */
        private void resize(int width) {
            int delta = width - getSizeWidth();
            if (delta == 0) return;
            setSize(new Size(width, UISizes.CONTROL_HEIGHT));
            tooltipsRight += delta;
            textRight += delta;
            if (rightButton != null) rightButton.setSelfPosition(new Position(rightButton.getSelfPositionX() + delta, 0));
            for (var tool : rightTools) tool.setSelfPosition(new Position(tool.getSelfPositionX() + delta, tool.getSelfPositionY()));
            if (contentWidget instanceof UIElement element) {
                int contentWidth = Math.max(0, textRight - textLeft);
                element.layout(l -> l.width(contentWidth));
            }
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            if (page == null) return;
            int x = getPositionX(), y = getPositionY(), h = getSizeHeight();
            int right = tooltipsRight;
            var tooltips = tooltipsPanel.getTooltips();
            for (int i = 0; i < tooltips.size(); i++) {
                if (!tooltipsPanel.isShown(i)) continue;
                var tooltip = tooltips.get(i);
                right -= ICON;
                tooltip.getFancyTooltipIcon().draw(graphics, mouseX, mouseY, x + right, UIPixels.center(y, h, ICON), ICON, ICON);
                right -= UISizes.GAP;
            }
            if (customContent) return;
            // 标题只让开实际显示的说明图标（部件挂上的说明很多，大多不显示，按总数预留会把标题挤成省略号）；
            // 去掉名称里的颜色代码，标题统一用正文色
            var title = ChatFormatting.stripFormatting(page.getTitle().getString());
            var text = UIText.fit(title == null ? "" : title, right - textLeft);
            UIText.drawLeft(graphics, text, x + textLeft, UIText.centerY(y, h), UITheme.TEXT);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            if (gui == null || gui.getModularUIGui() == null || page == null) return;
            var tooltip = hoveredTooltip(mouseX, mouseY);
            if (tooltip != null) {
                gui.getModularUIGui().setHoverTooltip(tooltip.getFancyTooltip(), ItemStack.EMPTY, null, tooltip.getFancyComponent());
            }
        }

        @Nullable
        private IFancyTooltip hoveredTooltip(int mouseX, int mouseY) {
            int x = getPositionX(), y = getPositionY() + (getSizeHeight() - ICON) / 2;
            int right = tooltipsRight;
            var tooltips = tooltipsPanel.getTooltips();
            for (int i = 0; i < tooltips.size(); i++) {
                if (!tooltipsPanel.isShown(i)) continue;
                right -= ICON;
                if (isMouseOver(x + right, y, ICON, ICON, mouseX, mouseY)) return tooltips.get(i);
                right -= UISizes.GAP;
            }
            return null;
        }
    }

    private final class Navigator extends Widget implements UIChannel.Host {

        private final UIChannel channel = new UIChannel(this);

        private Navigator() {
            super(0, 0, 0, 0);
        }

        @Override
        public UIChannel getChannel() {
            return channel;
        }

        @Override
        public void initWidget() {
            super.initWidget();
            channel.prime();
        }

        @Override
        public void writeInitialData(FriendlyByteBuf buffer) {
            super.writeInitialData(buffer);
            channel.writeInitialData(buffer);
        }

        @Override
        public void readInitialData(FriendlyByteBuf buffer) {
            super.readInitialData(buffer);
            channel.readInitialData(buffer);
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
    }
}
