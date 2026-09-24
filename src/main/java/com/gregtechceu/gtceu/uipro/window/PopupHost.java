package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.uipro.IShiftClickPriority;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.Label;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * {@link MachineWindow} 右侧的弹出面板容器。
 * <p>
 * 页面按需注册任意种面板（可以一种都没有）；每种面板（一个键）同时至多打开一个，用不同参数再次打开时原地替换；
 * 不同种的面板可以同时打开，按打开顺序从上往下排，放不下主窗口高度时另起一列往右排。
 * 没有打开任何面板时容器尺寸为 0，不占位置。
 * <p>
 * 面板状态属于这一个打开的界面（每名玩家各自独立），以服务端为准：客户端只发"请求打开/关闭"，
 * 服务端先通知客户端按同样的键和参数构建，再构建自己的那份，新控件的初始数据随后经 {@code addWidget} 的初始化通道下发。
 * 子控件顺序即打开顺序，两端执行同样的增删，下标一致。切换页面时两端各自 {@link #reset()}，不发包。
 * <p>
 * 容器本身只由 {@link MachineWindow} 使用；对外只公开 {@link #standalonePanel}，供不在 MachineWindow 里的控件复用同一面板外观。
 */
public final class PopupHost extends UIElement {

    private static final int OPEN_ID = SyncValueHost.ID_BASE - 3;
    private static final int CLOSE_ID = SyncValueHost.ID_BASE - 4;
    private static final int MAX_KEY_LENGTH = 64;
    /// 上次处理打开请求的游戏刻（服务端）
    private long lastOpenTick = Long.MIN_VALUE + 1;
    // WidgetGroup 转发子控件消息、下发新子控件初始数据用的 ID，包体第一个值都是子控件下标
    private static final int CHILD_UPDATE_ID = 1;
    private static final int CHILD_INIT_ID = 2;

    private final Map<String, IntFunction<Popup>> factories = new HashMap<>();
    /** 已打开的面板，与子控件一一对应、顺序相同。 */
    private final List<OpenPopup> opened = new ArrayList<>();
    private int maxHeight = Integer.MAX_VALUE;

    private record OpenPopup(String key, int argument) {}

    /// 面板按打开顺序纵向排，超过高度上限换到右边一列（flex-wrap），各面板保持自身宽度；尺寸变化经 {@link com.gregtechceu.gtceu.uipro.ILayoutHost} 通知窗口
    PopupHost() {
        layout(l -> l.column().flexWrap(FlexWrap.WRAP).alignStart().alignContent(AlignContent.FLEX_START)
                .rowGap(UISizes.SECTION_GAP).columnGap(UISizes.POPUP_GAP));
    }

    void register(String key, IntFunction<Popup> factory) {
        if (key.length() > MAX_KEY_LENGTH) throw new IllegalArgumentException("Popup key too long: " + key);
        factories.put(key, factory);
    }

    /** 每个面板的高度上限（客户端按屏幕高度设定，见 {@link UISizes#POPUP_SCREEN_MARGIN}；服务端不限），一列排满这个高度后另起一列。 */
    void setMaxHeight(int maxHeight) {
        if (this.maxHeight == maxHeight) return;
        this.maxHeight = maxHeight;
        for (var widget : widgets) {
            if (widget instanceof PopupPanel panel) panel.setMaxHeight(maxHeight);
        }
        layout(l -> l.maxHeight(maxHeight == Integer.MAX_VALUE ? LayoutStyle.AUTO : maxHeight));
    }

    /** 本端清空注册表并关闭所有面板（切换页面时两端各自调用）。 */
    void reset() {
        factories.clear();
        opened.clear();
        clearAllWidgets();
    }

    boolean isOpen(String key, int argument) {
        int index = indexOf(key);
        return index >= 0 && opened.get(index).argument() == argument;
    }

    boolean isOpen(String key) {
        return indexOf(key) >= 0;
    }

    int argumentOf(String key) {
        int index = indexOf(key);
        return index < 0 ? -1 : opened.get(index).argument();
    }

    void open(String key, int argument) {
        if (isRemote()) {
            writeClientAction(OPEN_ID, buf -> {
                buf.writeUtf(key, MAX_KEY_LENGTH);
                buf.writeVarInt(argument);
            });
        } else {
            serverOpen(key, argument);
        }
    }

    void close(String key) {
        if (isRemote()) writeClientAction(CLOSE_ID, buf -> buf.writeUtf(key, MAX_KEY_LENGTH));
        else serverClose(key);
    }

    void closeAll() {
        for (var popup : List.copyOf(opened)) close(popup.key());
    }

    private void serverOpen(String key, int argument) {
        // 同一种面板已按这个参数打开：状态不变，忽略。客户端重复发请求（连点、刷包）不会让服务端反复重建面板、整段重发初始数据
        if (isOpen(key, argument)) return;
        var popup = create(key, argument);
        if (popup == null) return;
        writeUpdateInfo(OPEN_ID, buf -> {
            buf.writeUtf(key, MAX_KEY_LENGTH);
            buf.writeVarInt(argument);
        });
        openLocal(key, argument, popup);
    }

    private void serverClose(String key) {
        if (indexOf(key) < 0) return;
        writeUpdateInfo(CLOSE_ID, buf -> buf.writeUtf(key, MAX_KEY_LENGTH));
        closeLocal(key);
    }

    /** 未注册的键或工厂拒绝该参数（如槽号越界）时返回 null。 */
    @Nullable
    private Popup create(String key, int argument) {
        var factory = factories.get(key);
        return factory == null ? null : factory.apply(argument);
    }

    /** 同一种面板已打开时原地替换（保持下标），否则追加到末尾。 */
    private void openLocal(String key, int argument, Popup popup) {
        var panel = new PopupPanel(key, popup, maxHeight, () -> close(key));
        int index = indexOf(key);
        if (index >= 0) {
            removeWidget(widgets.get(index));
            opened.set(index, new OpenPopup(key, argument));
            addWidget(index, panel);
        } else {
            opened.add(new OpenPopup(key, argument));
            addWidget(panel);
        }
    }

    private void closeLocal(String key) {
        int index = indexOf(key);
        if (index < 0) return;
        opened.remove(index);
        removeWidget(widgets.get(index));
    }

    private int indexOf(String key) {
        for (int i = 0; i < opened.size(); i++) {
            if (opened.get(i).key().equals(key)) return i;
        }
        return -1;
    }

    // ==================== 同步 ====================

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == OPEN_ID) {
            // 每次打开都要在服务端建面板并下发初始数据，同一刻只处理一次，防止开关交替刷包
            long tick = gui != null ? gui.entityPlayer.level().getGameTime() : Long.MIN_VALUE;
            if (tick == lastOpenTick) return;
            lastOpenTick = tick;
            serverOpen(buffer.readUtf(MAX_KEY_LENGTH), buffer.readVarInt());
        } else if (id == CLOSE_ID) {
            serverClose(buffer.readUtf(MAX_KEY_LENGTH));
        } else if (id != CHILD_UPDATE_ID || hasChild(buffer)) {
            // 客户端操作的面板已被服务端关掉时丢弃
            super.handleClientAction(id, buffer);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == OPEN_ID) {
            var key = buffer.readUtf(MAX_KEY_LENGTH);
            int argument = buffer.readVarInt();
            var popup = create(key, argument);
            if (popup != null) openLocal(key, argument, popup);
            else closeLocal(key);
        } else if (id == CLOSE_ID) {
            closeLocal(buffer.readUtf(MAX_KEY_LENGTH));
        } else if ((id != CHILD_UPDATE_ID && id != CHILD_INIT_ID) || hasChild(buffer)) {
            // 客户端已切换页面、面板已清空，服务端还在为旧面板发数据时丢弃
            super.readUpdateInfo(id, buffer);
        }
    }

    /** 看一眼包里的子控件下标是否存在（不消耗读指针）。 */
    private boolean hasChild(FriendlyByteBuf buffer) {
        buffer.markReaderIndex();
        int index = buffer.readVarInt();
        buffer.resetReaderIndex();
        return index >= 0 && index < widgets.size();
    }

    /**
     * 单独的一个面板，外观与右侧弹出面板完全相同（Ore 窗口外框、标题行与 {@code [×]}、高度随内容的滚动区、Shift+点击优先），
     * 给不在 {@link MachineWindow} 里、没有面板容器可用的控件做退路。面板挂到哪里、何时开关、两端怎样保持一致都由调用方负责；
     * {@code close} 在客户端点 {@code [×]} 时执行。高度不设上限（没有窗口可参照屏幕高度）。
     */
    public static UIElement standalonePanel(String key, Popup popup, Runnable close) {
        return new PopupPanel(key, popup, Integer.MAX_VALUE, close);
    }

    /**
     * 面板本体：Ore 窗口外框，标题行 + 高度随内容的滚动区。宽度随内容（flexbox 自适应）：滚动区宽度跟随内容，
     * 放得下时不留滚动条的位置，要滚动时向右加宽；标题行由交叉轴拉伸跟到同宽。
     */
    private static final class PopupPanel extends UIElement implements IShiftClickPriority {

        /// 标题行、内边距、间距占掉的高度，面板高度上限减去它就是滚动区的高度上限
        private static final int CHROME = UISizes.POPUP_PADDING + UISizes.CONTROL_HEIGHT + UISizes.SECTION_GAP + UISizes.POPUP_PADDING_BOTTOM;

        private final ScrollerView scroller;

        private PopupPanel(String key, Popup popup, int maxHeight, Runnable close) {
            layout(l -> l.column().gapAll(UISizes.SECTION_GAP).paddingAll(UISizes.POPUP_PADDING).paddingBottom(UISizes.POPUP_PADDING_BOTTOM));
            setBackground(UITheme.WINDOW);

            var closeButton = Button.glyph("×").setOnClientClick(close);
            closeButton.setHoverTooltips(MachineWindow.POPUP_CLOSE);
            var titleRow = UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                    .addChildren(Label.of(popup.title(), UISizes.POPUP_CONTENT_WIDTH - UISizes.ICON_BUTTON - UISizes.GAP), UIElement.flexSpacer(), closeButton);

            // 内容至少一个 9 槽区块宽（按 getContentWidth 定宽的页面照旧），里面的滚动区被拖宽时跟着变宽
            var content = new UIElement().layout(l -> l.column().widthAuto().minWidth(UISizes.POPUP_CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
            popup.content().accept(content);
            // 初始尺寸取最小，宽高都跟随内容（见 ScrollerView 对滚动范围的处理）
            // 滚动区 id 按面板的键取：同一种面板锁定的高度重开时沿用
            scroller = new ScrollerView("popup." + key, UISizes.POPUP_CONTENT_WIDTH, UISizes.SLOT).adaptiveWidth();
            scroller.addScrollViewChild(content);
            scroller.adaptiveHeight(contentLimit(maxHeight));
            addChildren(titleRow, scroller);
        }

        private static int contentLimit(int maxHeight) {
            return Math.max(UISizes.SLOT, maxHeight - CHROME);
        }

        /** 弹出面板是玩家正在操作的窗口：Shift+点击时面板里的槽优先接收。 */
        @Override
        public int getShiftClickPriority() {
            return IShiftClickPriority.POPUP;
        }

        /** 屏幕尺寸变化时更新滚动区高度上限（内容放得下就不滚动）。 */
        private void setMaxHeight(int maxHeight) {
            scroller.adaptiveHeight(contentLimit(maxHeight));
        }
    }
}
