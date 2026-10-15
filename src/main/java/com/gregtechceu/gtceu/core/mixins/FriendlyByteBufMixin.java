package com.gregtechceu.gtceu.core.mixins;

import com.gto.datasynclib.util.ComponentStreamCodecs;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 组件的网络读写改走 DataSyncLib 的二进制编码。
 *
 * <p>原版把整棵组件树写成 JSON 字符串：字段名、引号、转义、再加一个 UTF-8 长度前缀，聊天、标题、
 * 计分板、工具提示这些每次同步或每帧都要付这份开销。二进制形式只写"种类字节 + 该种类的字段本身"
 * （见 {@link ComponentStreamCodecs}），常见的一行文本从 14 字节降到 7 字节，带样式的多行提示能省
 * 一半以上。</p>
 *
 * <p>{@code writeComponent(null)} 在原版是合法的（写一个 JSON {@code null}），所以这里先写一个存在
 * 标记字节再写组件，读侧按同一个标记返回 {@code null}。</p>
 *
 * <p><b>这是全网统一的线格式改动</b>：两端都必须有 GTM，缺少这个 mixin 的一方会把二进制载荷当成
 * JSON 读。原版客户端/服务端、或没装 GTM 的 Forge 端，都不能再与本端交换组件。</p>
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin {

    @Inject(method = "writeComponent", at = @At("HEAD"), cancellable = true)
    private void gtceu$writeComponent(Component component, CallbackInfoReturnable<FriendlyByteBuf> cir) {
        var buf = (FriendlyByteBuf) (Object) this;
        buf.writeBoolean(component != null);
        if (component != null) {
            ComponentStreamCodecs.COMPONENT.encode(buf, component);
        }
        cir.setReturnValue(buf);
    }

    @Inject(method = "readComponent", at = @At("HEAD"), cancellable = true)
    private void gtceu$readComponent(CallbackInfoReturnable<Component> cir) {
        var buf = (FriendlyByteBuf) (Object) this;
        cir.setReturnValue(buf.readBoolean() ? ComponentStreamCodecs.COMPONENT.decode(buf) : null);
    }
}
