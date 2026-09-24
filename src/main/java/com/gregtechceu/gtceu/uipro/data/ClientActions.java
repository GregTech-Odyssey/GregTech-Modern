package com.gregtechceu.gtceu.uipro.data;

import com.gregtechceu.gtceu.uipro.ILocalUI;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;

import io.netty.buffer.Unpooled;

import java.util.function.Consumer;

/**
 * 没有服务端的界面里的客户端请求（client action）。
 * <p>
 * 普通界面的请求经 LDLib 发给服务端，由控件的 {@code handleClientAction} 校验后执行；
 * 没有服务端的界面（根实现 {@link ILocalUI}，如 EMI 里的配方页）里，LDLib 会把请求直接丢掉。
 * 发请求的地方统一写成
 * {@code if (!ClientActions.handleLocally(this, id, writer)) writeClientAction(id, writer);}，
 * 没有服务端时在本端立即交给同一个 {@code handleClientAction}，两种界面走同一套处理代码。
 */
public final class ClientActions {

    private ClientActions() {}

    /** @return 控件在没有服务端的界面里、请求已在本端处理；为 false 时由调用方照常 {@code writeClientAction} */
    public static boolean handleLocally(Widget widget, int id, Consumer<FriendlyByteBuf> writer) {
        if (!ILocalUI.isLocal(widget)) return false;
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            widget.handleClientAction(id, buffer);
        } finally {
            buffer.release();
        }
        return true;
    }
}
