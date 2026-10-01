package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.uipro.data.UIFaults;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 服务端打开界面出错时只拒绝打开并记录，不让异常沿方块交互一路抛到服务器主循环；已建好的界面模板照常触发关闭回调。
 */
@Mixin(value = UIFactory.class, remap = false)
public abstract class UIFactoryFaultMixin {

    @WrapMethod(method = "openUI")
    private boolean gtceu$isolateOpenFailure(Object holder, ServerPlayer player, Operation<Boolean> original) {
        var previous = player.containerMenu;
        try {
            return original.call(holder, player);
        } catch (RuntimeException | LinkageError e) {
            UIFaults.openFailed(this, holder, player, previous, e);
            return false;
        }
    }

    @WrapOperation(method = "openUI", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/modular/ModularUI;initWidgets()V"))
    private void gtceu$releaseOnInitFailure(ModularUI ui, Operation<Void> original) {
        try {
            original.call(ui);
        } catch (RuntimeException | LinkageError e) {
            UIFaults.release(ui);
            throw e;
        }
    }

    @WrapOperation(method = "openUI",
                   at = @At(value = "INVOKE",
                            target = "Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;writeInitialData(Lnet/minecraft/network/FriendlyByteBuf;)V"))
    private void gtceu$releaseOnWriteFailure(WidgetGroup group, FriendlyByteBuf buf, Operation<Void> original, @Local ModularUI ui) {
        try {
            original.call(group, buf);
        } catch (RuntimeException | LinkageError e) {
            UIFaults.release(ui);
            throw e;
        }
    }
}
