package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.uipro.data.UIBudget;
import com.gregtechceu.gtceu.uipro.data.UIFaults;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.networking.c2s.CPacketUIClientAction;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 服务端每刻同步与客户端请求出错时关闭该玩家的界面并记录，不让一个界面的错误拖垮整台服务器或被网络线程静默吞掉。
 */
@Mixin(value = ModularUIContainer.class, remap = false)
public abstract class ModularUIContainerFaultMixin {

    @Shadow
    @Final
    private ModularUI modularUI;

    @WrapMethod(method = "broadcastChanges", remap = true)
    private void gtceu$isolateSyncFailure(Operation<Void> original) {
        try {
            original.call();
        } catch (RuntimeException | LinkageError e) {
            UIFaults.syncFailed(modularUI, e);
        }
    }

    @WrapMethod(method = "handleClientAction")
    private void gtceu$isolateActionFailure(CPacketUIClientAction packet, Operation<Void> original) {
        var player = modularUI.entityPlayer;
        if (player != null && !UIBudget.of(modularUI).tryConsume(player.level().getGameTime(), UIBudget.REQUEST_COST)) return;
        try {
            original.call(packet);
        } catch (RuntimeException | LinkageError e) {
            UIFaults.actionFailed(modularUI, e);
        }
    }
}
