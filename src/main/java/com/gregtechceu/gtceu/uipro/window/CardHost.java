package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.IntFunction;

/**
 * 卡片位：放在界面任意位置（常见是浮在画布等内容之上，见 {@code CanvasView#addFloatingCard}）的一张卡片（{@link PopupCard}），
 * 按整数参数打开（如节点编码），换参数原地替换，关闭后不占位置。外观与机器窗口右侧的弹出面板相同；
 * 高度跟随所在区域（{@link #setMaxHeight}），滚动区不能拖拽缩放、不套用锁定的高度。
 * <p>
 * 状态以服务端为准，做法同 {@link PopupHost}：客户端只发"请求打开 / 关闭"，服务端用工厂校验参数（返回 null 即拒绝并回告），
 * 先通知客户端按同样的参数构建，再构建自己的那份，新控件的初始数据经 {@code addWidget} 的初始化通道下发。
 * 请求发出后、回应到达前，客户端上的旧卡片不再接受操作：两端卡片都在同一个子控件下标，旧卡片上的点击会落到服务端刚换上的新卡片。
 * 没有服务端的界面（{@link ILocalUI}，如 EMI 配方页）里，请求直接在本端执行。
 * 界面初始化之前（如刚建好、还没挂进界面的页面）发起的打开请求，等初始化时再发出。
 * 卡片状态属于这一个打开的界面（每名玩家各自独立），不进机器字段。
 */
public class CardHost extends UIElement {

    /// 更新 / 请求 ID：避开 WidgetGroup 的 1、2；SyncValueHost 段以下的登记见 Adjuster
    private static final int OPEN_ID = SyncValueHost.ID_BASE - 7;
    private static final int CLOSE_ID = SyncValueHost.ID_BASE - 8;
    /// 服务端拒绝了打开请求（参数不合法）：客户端清掉"已请求"状态、恢复旧卡片
    private static final int REJECT_ID = SyncValueHost.ID_BASE - 9;
    // WidgetGroup 转发子控件消息、下发新子控件初始数据用的 ID，包体第一个值都是子控件下标
    private static final int CHILD_UPDATE_ID = 1;
    private static final int CHILD_INIT_ID = 2;

    private final String id;
    private final IntFunction<Popup> factory;
    private int argument = -1;
    /// 客户端已请求打开、服务端还没回应的参数，-1 为没有（布局提前按"卡片要出现"安排，如画布定位时让出卡片的位置）；
    /// 界面初始化之前也用它记下要打开的参数
    private int requested = -1;
    private int maxHeight = Integer.MAX_VALUE;

    /**
     * @param id      固定 id（卡片滚动区的 id 为 {@code card.<id>}）
     * @param factory 按参数建卡片（两端都会调用）；参数不合法（可能来自客户端）时返回 null 拒绝打开
     */
    public CardHost(String id, IntFunction<Popup> factory) {
        this.id = id;
        this.factory = factory;
        layout(l -> l.column());
    }

    /** 打开着的卡片的参数，没打开为 -1（客户端可直接读，用于高亮对应项）。 */
    public int getArgument() {
        return argument;
    }

    public boolean isOpen() {
        return argument >= 0;
    }

    /** 已打开，或客户端已请求打开、正等服务端回应。 */
    public boolean isOpenOrRequested() {
        return argument >= 0 || requested >= 0;
    }

    /** 卡片高度上限（所在区域的可用高度，客户端按布局设定）。 */
    public void setMaxHeight(int maxHeight) {
        if (this.maxHeight == maxHeight) return;
        this.maxHeight = maxHeight;
        for (var widget : widgets) {
            if (widget instanceof PopupCard card) card.setMaxHeight(maxHeight);
        }
    }

    /** 打开（或换成）参数为 {@code argument} 的卡片；客户端调用时请求服务端，两端随后一起构建。 */
    public void open(int argument) {
        if (!isInitialized()) {
            requested = argument;
        } else if (ILocalUI.isLocal(this)) {
            var popup = factory.apply(argument);
            if (popup != null) openLocal(argument, popup);
        } else if (isRemote()) {
            requested = argument;
            setCardActive(false);
            writeClientAction(OPEN_ID, buf -> buf.writeVarInt(argument));
        } else {
            serverOpen(argument);
        }
    }

    public void close() {
        if (!isInitialized()) {
            requested = -1;
        } else if (ILocalUI.isLocal(this)) {
            closeLocal();
        } else if (isRemote()) {
            requested = -1;
            setCardActive(false);
            writeClientAction(CLOSE_ID, buf -> {});
        } else {
            serverClose();
        }
    }

    /**
     * 以同样参数打开着（或已请求打开）时关闭，否则打开（或换成这个参数）。
     *
     * @return 是否是打开
     */
    public boolean toggle(int argument) {
        if (this.argument == argument || requested == argument) {
            close();
            return false;
        }
        open(argument);
        return true;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        // 初始化之前记下的打开请求
        if (requested >= 0) {
            int pending = requested;
            requested = -1;
            open(pending);
        }
    }

    private void serverOpen(int argument) {
        var popup = factory.apply(argument);
        if (popup == null) {
            writeUpdateInfo(REJECT_ID, buf -> {});
            return;
        }
        writeUpdateInfo(OPEN_ID, buf -> buf.writeVarInt(argument));
        openLocal(argument, popup);
    }

    private void serverClose() {
        if (argument < 0) return;
        writeUpdateInfo(CLOSE_ID, buf -> {});
        closeLocal();
    }

    private void openLocal(int argument, Popup popup) {
        clearAllWidgets();
        this.argument = argument;
        requested = -1;
        addWidget(new PopupCard("card." + id, popup, maxHeight, this::close).setResizable(false));
    }

    private void closeLocal() {
        argument = -1;
        requested = -1;
        clearAllWidgets();
    }

    private void setCardActive(boolean active) {
        for (var widget : widgets) widget.setActive(active);
    }

    // ==================== 同步 ====================

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == OPEN_ID) {
            serverOpen(buffer.readVarInt());
        } else if (id == CLOSE_ID) {
            serverClose();
        } else if (id != CHILD_UPDATE_ID || hasChild(buffer)) {
            // 客户端操作的卡片已被服务端换掉时丢弃
            super.handleClientAction(id, buffer);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == OPEN_ID) {
            int argument = buffer.readVarInt();
            var popup = factory.apply(argument);
            if (popup != null) openLocal(argument, popup);
            else closeLocal();
        } else if (id == CLOSE_ID) {
            closeLocal();
        } else if (id == REJECT_ID) {
            requested = -1;
            setCardActive(true);
        } else if ((id != CHILD_UPDATE_ID && id != CHILD_INIT_ID) || hasChild(buffer)) {
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
}
