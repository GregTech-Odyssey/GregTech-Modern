package com.gregtechceu.gtceu.uipro;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

/**
 * 没有服务端的界面的根（如 EMI 里的配方页）：整棵界面只在客户端存在，数据全部来自本端。
 * <p>
 * 这类界面里，框架把"服务端"的那一半也放在本端执行：{@link com.gregtechceu.gtceu.uipro.data.SyncValue} 直接取本端 getter，
 * {@code Button.setOnServerClick} 的回调在本端执行，客户端请求（{@link com.gregtechceu.gtceu.uipro.data.ClientActions}）交给本端的
 * {@code handleClientAction}。
 * <p>
 * 判定看的是根是否实现本接口，而不是控件的 {@code isClientSideWidget}：有服务端的界面里也会有只在客户端的子树，
 * 那里的"服务端"回调不能在客户端执行。
 */
public interface ILocalUI {

    /** {@code widget} 是否位于没有服务端的界面里（沿父链找本接口）。 */
    static boolean isLocal(Widget widget) {
        for (var current = widget; current != null; current = current.getParent()) {
            if (current instanceof ILocalUI) return true;
        }
        return false;
    }
}
