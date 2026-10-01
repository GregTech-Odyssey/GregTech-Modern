package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.FriendlyByteBuf;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 服务端路由客户端请求时，子控件下标越界（结构已变、请求过期或伪造）直接丢弃，不再抛异常。
 */
@Mixin(value = WidgetGroup.class, remap = false)
public abstract class WidgetGroupRoutingMixin {

    @Shadow
    @Final
    public List<Widget> widgets;

    @Inject(method = "handleClientAction", at = @At("HEAD"), cancellable = true)
    private void gtceu$dropStaleRoute(int id, FriendlyByteBuf buffer, CallbackInfo ci) {
        if (id != 1) return;
        if (!buffer.isReadable()) {
            ci.cancel();
            return;
        }
        int start = buffer.readerIndex();
        int index = buffer.readVarInt();
        buffer.readerIndex(start);
        if (index < 0 || index >= widgets.size()) ci.cancel();
    }
}
