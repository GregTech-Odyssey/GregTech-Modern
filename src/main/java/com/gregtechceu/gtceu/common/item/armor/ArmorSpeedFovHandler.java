package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * GTO: 护腿疾跑倍率通过移动速度属性实现，原版视野会随速度放大（6 倍时严重变形）。
 * 这里把倍率从视野计算中除掉，视野只随原版疾跑等因素变化。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = GTCEu.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorSpeedFovHandler {

    private ArmorSpeedFovHandler() {}

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        double multiplier = ArmorLogicSuite.getLeggingsSpeedMultiplier(event.getPlayer());
        if (multiplier <= 1) return;
        // 原版：视野系数 = (移动速度 / 行走速度 + 1) / 2，速度被护腿放大 multiplier 倍，这里还原
        float modifier = event.getNewFovModifier();
        event.setNewFovModifier((float) (((modifier * 2 - 1) / multiplier + 1) / 2));
    }
}
