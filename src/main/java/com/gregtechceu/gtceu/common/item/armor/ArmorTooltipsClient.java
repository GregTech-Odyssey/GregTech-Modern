package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.List;

/**
 * GTO: {@link ArmorTooltips} 里只在客户端可用的部分，单独成类避免服务端加载客户端类
 */
@OnlyIn(Dist.CLIENT)
final class ArmorTooltipsClient {

    private ArmorTooltipsClient() {}

    static boolean isUnbound(KeyBind key) {
        return key.toMinecraft() == null || key.toMinecraft().isUnbound();
    }

    static Component keyName(KeyBind key) {
        return key.toMinecraft().getTranslatedKeyMessage();
    }

    static boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    static List<ItemStack> wornArmor() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? Collections.emptyList() : player.getInventory().armor;
    }
}
