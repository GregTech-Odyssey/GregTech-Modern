package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.UIStructure;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.IntFunction;

/**
 * 卡片位：放在界面任意位置（常见是浮在画布等内容之上，见 {@code CanvasView#addFloatingCard}）的一张卡片（{@link PopupCard}），
 * 按整数参数打开（如节点编码），换参数原地替换，关闭后不占位置。外观与机器窗口右侧的弹出面板相同；
 * 高度跟随所在区域（{@link #setMaxHeight}），滚动区不能拖拽缩放、不套用锁定的高度。
 * <p>
 * 打开的卡片是服务端权威的结构状态（{@link UIStructure}）：客户端只发请求，服务端用工厂校验参数（返回 null 即拒绝并回告），
 * 先通知客户端按同样的参数构建，再构建自己的那份；当前卡片写在初始数据里，旧卡片上的过期请求按纪元丢弃。
 * 没有服务端的界面（{@link ILocalUI}，如 EMI 配方页）里，请求直接在本端执行。
 * 服务端在界面初始化之前发起的打开请求，等初始化时执行并随初始数据下发。
 * 卡片状态属于这一个打开的界面（每名玩家各自独立），不进机器字段。
 */
public class CardHost extends UIElement {

    private final String id;
    private final IntFunction<Popup> factory;
    private final UIStructure<Integer> card;
    private int argument = -1;
    private int initialArgument = -1;
    private int maxHeight = Integer.MAX_VALUE;

    /**
     * @param id      固定 id（卡片滚动区的 id 为 {@code card.<id>}）
     * @param factory 按参数建卡片（两端都会调用）；参数不合法（可能来自客户端）时返回 null 拒绝打开
     */
    public CardHost(String id, IntFunction<Popup> factory) {
        this.id = id;
        this.factory = factory;
        layout(l -> l.column());
        card = addStructure(ByteStreamCodec.INT_CODEC, () -> argument)
                .validate(argument -> argument >= -1)
                .prepare(this::prepare)
                .apply(this::show);
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
        return argument >= 0 || card.getTarget() >= 0;
    }

    /** 卡片高度上限（所在区域的可用高度，客户端按布局设定）。 */
    public void setMaxHeight(int maxHeight) {
        if (this.maxHeight == maxHeight) return;
        this.maxHeight = maxHeight;
        for (var widget : widgets) {
            if (widget instanceof PopupCard popupCard) popupCard.setMaxHeight(maxHeight);
        }
    }

    /** 打开（或换成）参数为 {@code argument} 的卡片；客户端调用时请求服务端，两端随后一起构建。 */
    public void open(int argument) {
        request(argument < 0 ? -1 : argument);
    }

    public void close() {
        request(-1);
    }

    /**
     * 以同样参数打开着（或已请求打开）时关闭，否则打开（或换成这个参数）。
     *
     * @return 是否是打开
     */
    public boolean toggle(int argument) {
        if (this.argument == argument || card.getTarget() == argument) {
            close();
            return false;
        }
        open(argument);
        return true;
    }

    @Override
    public void initWidget() {
        int pending = initialArgument;
        initialArgument = -1;
        boolean applyNow = pending >= 0 && (!isRemote() || ILocalUI.isLocal(this));
        if (applyNow) card.request(pending);
        super.initWidget();
        if (pending >= 0 && !applyNow) card.request(pending);
    }

    private void request(int argument) {
        if (!isInitialized()) {
            initialArgument = argument;
            return;
        }
        card.request(argument);
    }

    @Nullable
    private Optional<Popup> prepare(int argument) {
        if (argument < 0) return Optional.empty();
        var popup = factory.apply(argument);
        return popup == null ? null : Optional.of(popup);
    }

    private void show(int argument, @Nullable Optional<Popup> prepared) {
        var popup = prepared != null ? prepared.orElse(null) : argument < 0 ? null : factory.apply(argument);
        clearAllWidgets();
        if (popup == null) {
            this.argument = -1;
            return;
        }
        this.argument = argument;
        addWidget(new PopupCard("card." + id, popup, maxHeight, this::close).setResizable(false));
    }
}
