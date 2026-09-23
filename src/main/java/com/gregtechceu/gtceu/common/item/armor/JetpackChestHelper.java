package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;
import com.gregtechceu.gtceu.api.item.armor.ArmorUtils;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

/**
 * GTO: 带喷气背包的胸甲（纳米肌体进阶 II、夸克高科 III、夸克高科进阶 IV）共用的逻辑。
 * <p>
 * 开关只在服务端、按键按下沿切换一次，再随物品同步到客户端；功能总能开启，条件不足时提示"暂不生效"。
 * 给背包充电每个玩家独立计算，不在共享的逻辑对象上保存玩家状态。
 */
public final class JetpackChestHelper {

    /**
     * 飞行、悬停持续耗电，单位 A/s（按胸甲自身电压计，实际每 tick 扣 1/20）
     */
    public static final double FLIGHT_AMPS = 3.5;

    public static final String FLIGHT = "enabled";
    public static final String HOVER = "hover";
    public static final String EMERGENCY_HOVER = "emergencyHover";
    public static final String CHARGE = "canShare";

    private JetpackChestHelper() {}

    public static boolean isFlightEnabled(CompoundTag data) {
        return data == null || !data.contains(FLIGHT) || data.getBoolean(FLIGHT);
    }

    public static boolean isEmergencyHoverEnabled(CompoundTag data) {
        return data == null || !data.contains(EMERGENCY_HOVER) || data.getBoolean(EMERGENCY_HOVER);
    }

    public static <T extends ArmorLogicSuite & IJetpack> void tick(T suite, Level world, Player player, ItemStack item) {
        IElectricItem cont = GTCapabilityHelper.getElectricItem(item);
        if (cont == null) return;
        CompoundTag data = item.getOrCreateTag();
        boolean flight = isFlightEnabled(data);
        boolean hover = data.getBoolean(HOVER);
        boolean emergencyHover = isEmergencyHoverEnabled(data);
        boolean canShare = data.getBoolean(CHARGE);

        if (!world.isClientSide) {
            // 缺省值写入 NBT：ForgeCommonEventListener 的致命坠落判定直接读 emergencyHover
            if (!data.contains(FLIGHT)) data.putBoolean(FLIGHT, true);
            if (!data.contains(EMERGENCY_HOVER)) data.putBoolean(EMERGENCY_HOVER, true);
            boolean hasEnergy = suite.canUseEnergy(item, suite.getEnergyPerUse());
            if (KeyBind.JETPACK_ENABLE.consumePress(player)) {
                flight = !flight;
                data.putBoolean(FLIGHT, flight);
                notify(player, item, "jetpack", flight, hasEnergy);
            }
            if (KeyBind.ARMOR_HOVER.consumePress(player)) {
                hover = !hover;
                data.putBoolean(HOVER, hover);
                notify(player, item, "hover", hover, hasEnergy);
            }
            if (KeyBind.ARMOR_EMERGENCY_HOVER.consumePress(player)) {
                emergencyHover = !emergencyHover;
                data.putBoolean(EMERGENCY_HOVER, emergencyHover);
                notify(player, item, "emergency_hover", emergencyHover, hasEnergy);
            }
            if (KeyBind.ARMOR_CHARGING.consumePress(player)) {
                canShare = toggleCharging(player, item, data, cont);
            }
        }

        suite.performFlying(player, flight, hover, item);

        // 与原逻辑一致：每 10 tick 按各物品传输上限 ×10 充一次，即持续 1A
        if (canShare && !world.isClientSide && player.tickCount % 10 == 0) {
            chargeInventory(player, item, cont);
        }
    }

    /**
     * 手持胸甲潜行右键切换给背包充电
     */
    public static InteractionResultHolder<ItemStack> onShiftUse(Level world, Player player, InteractionHand hand) {
        ItemStack armor = player.getItemInHand(hand);
        IElectricItem cont = GTCapabilityHelper.getElectricItem(armor);
        if (cont == null) return InteractionResultHolder.fail(armor);
        if (!world.isClientSide) {
            toggleCharging(player, armor, armor.getOrCreateTag(), cont);
        }
        return InteractionResultHolder.success(armor);
    }

    private static boolean toggleCharging(Player player, ItemStack item, CompoundTag data, IElectricItem cont) {
        boolean canShare = !data.getBoolean(CHARGE);
        data.putBoolean(CHARGE, canShare);
        notify(player, item, "charge_items", canShare, cont.getCharge() > 0);
        return canShare;
    }

    private static void notify(Player player, ItemStack item, String name, boolean enabled, boolean effective) {
        player.displayClientMessage(ArmorTooltips.toggleMessage(item, name, enabled, effective), false);
    }

    private static void chargeInventory(Player player, ItemStack self, IElectricItem cont) {
        List<Pair<NonNullList<ItemStack>, IntList>> inventories = ArmorUtils.getChargeableItem(player, cont.getTier());
        boolean changed = false;
        for (int i = 0; i < inventories.size(); i++) {
            Pair<NonNullList<ItemStack>, IntList> inventory = inventories.get(i);
            IntList slots = inventory.getSecond();
            for (int j = 0; j < slots.size(); j++) {
                ItemStack stack = inventory.getFirst().get(slots.getInt(j));
                if (stack == self) continue;
                IElectricItem target = GTCapabilityHelper.getElectricItem(stack);
                if (target == null || target.getCharge() >= target.getMaxCharge()) continue;
                long attempt = target.getTransferLimit() * 10;
                if (!cont.canUse(attempt)) continue;
                long delta = target.charge(attempt, cont.getTier(), true, false);
                if (delta > 0) {
                    cont.discharge(delta, cont.getTier(), true, false, false);
                    changed = true;
                }
            }
        }
        if (changed) player.inventoryMenu.sendAllDataToRemote();
    }

    @OnlyIn(Dist.CLIENT)
    public static void addHudLines(ItemStack item, ArmorUtils.ModularHUD hud) {
        CompoundTag data = item.getTag();
        if (data == null) return;
        hud.newString(Component.translatable("metaarmor.hud.engine_enabled", status(isFlightEnabled(data))));
        hud.newString(Component.translatable("metaarmor.hud.hover_mode", status(data.getBoolean(HOVER))));
        hud.newString(Component.translatable("metaarmor.hud.emergency_hover_mode",
                status(isEmergencyHoverEnabled(data))));
        hud.newString(Component.translatable("mataarmor.hud.supply_mode", status(data.getBoolean(CHARGE))));
    }

    private static Component status(boolean enabled) {
        return Component.translatable(enabled ? "metaarmor.hud.status.enabled" : "metaarmor.hud.status.disabled");
    }
}
