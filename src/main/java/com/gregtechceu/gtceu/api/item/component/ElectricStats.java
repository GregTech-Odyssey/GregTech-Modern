package com.gregtechceu.gtceu.api.item.component;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.capability.ElectricItem;
import com.gregtechceu.gtceu.api.tooltip.StatKind;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.client.renderer.item.ToolChargeBarRenderer;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.List;

public class ElectricStats implements IInteractionItem, IAddInformation, IItemLifeCycle, IItemDecoratorComponent {

    public static final ElectricStats EMPTY = ElectricStats.create(0, 0, false, false);

    public final long maxCharge;
    public final int tier;

    public final boolean chargeable;
    public final boolean dischargeable;
    /**
     * GTO: 每 tick 最多可充入 / 放出的安培数，默认 1A
     */
    public long transferAmps = 1;

    protected ElectricStats(long maxCharge, int tier, boolean chargeable, boolean dischargeable) {
        this.maxCharge = maxCharge;
        this.tier = tier;
        this.chargeable = chargeable;
        this.dischargeable = dischargeable;
    }

    public static ElectricStats create(long maxCharge, int tier, boolean chargeable, boolean dischargeable) {
        return new ElectricStats(maxCharge, tier, chargeable, dischargeable);
    }

    public ElectricStats transferAmps(long amps) {
        this.transferAmps = amps;
        return this;
    }

    public static float getStoredPredicate(ItemStack itemStack) {
        var electricItem = GTCapabilityHelper.getElectricItem(itemStack);
        if (electricItem != null) {
            var per = (electricItem.getCharge() * 7 / electricItem.getMaxCharge());
            return per / 100f;
        }
        return 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        var itemStack = player.getItemInHand(usedHand);
        var electricItem = GTCapabilityHelper.getElectricItem(itemStack);
        if (electricItem != null && electricItem.canProvideChargeExternally() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                boolean isInDischargeMode = isInDischargeMode(itemStack);
                String locale = "metaitem.electric.discharge_mode." + (isInDischargeMode ? "disabled" : "enabled");
                player.displayClientMessage(Component.translatable(locale), true);
                setInDischargeMode(itemStack, !isInDischargeMode);
            }
            return InteractionResultHolder.success(itemStack);
        }
        return IInteractionItem.super.use(item, level, player, usedHand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        var electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (!level.isClientSide && entity instanceof Player player && electricItem != null &&
                electricItem.canProvideChargeExternally() &&
                isInDischargeMode(stack) && electricItem.getCharge() > 0L) {
            long transferLimit = electricItem.getTransferLimit();

            if (GTCEu.Mods.isCuriosLoaded()) {
                IItemHandler curios = CuriosApi.getCuriosInventory(player)
                        .<IItemHandler>map(ICuriosItemHandler::getEquippedCurios)
                        .orElse(EmptyHandler.INSTANCE);
                for (int i = 0; i < curios.getSlots(); i++) {
                    var itemInSlot = curios.getStackInSlot(i);
                    long chargedAmount = chargeItemStack(transferLimit, electricItem, itemInSlot);
                    if (chargedAmount > 0L) {
                        transferLimit -= chargedAmount;
                    }
                    if (transferLimit == 0L) break;
                }
            }

            var inventoryPlayer = player.getInventory();
            for (int i = 0; i < inventoryPlayer.getContainerSize(); i++) {
                var itemInSlot = inventoryPlayer.getItem(i);
                long chargedAmount = chargeItemStack(transferLimit, electricItem, itemInSlot);
                if (chargedAmount > 0L) {
                    transferLimit -= chargedAmount;
                }
                if (transferLimit == 0L) break;
            }
        }
    }

    private static long chargeItemStack(long maxDischargeAmount, IElectricItem source, ItemStack target) {
        var slotElectricItem = GTCapabilityHelper.getElectricItem(target);
        if (slotElectricItem != null && !slotElectricItem.canProvideChargeExternally()) {
            return chargeElectricItem(maxDischargeAmount, source, slotElectricItem);
        } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
            var feEnergyItem = GTCapabilityHelper.getForgeEnergyItem(target);
            if (feEnergyItem != null && feEnergyItem.canReceive() &&
                    feEnergyItem.getEnergyStored() < feEnergyItem.getMaxEnergyStored()) {
                return chargeForgeEnergyItem(maxDischargeAmount, source, feEnergyItem);
            }
        }
        return 0;
    }

    private static long chargeElectricItem(long maxDischargeAmount, IElectricItem source, IElectricItem target) {
        long maxDischarged = source.discharge(maxDischargeAmount, source.getTier(), false, false, true);
        long maxReceived = target.charge(maxDischarged, source.getTier(), false, true);
        if (maxReceived > 0L) {
            long resultDischarged = source.discharge(maxReceived, source.getTier(), false, true, false);
            target.charge(resultDischarged, source.getTier(), false, false);
            return resultDischarged;
        }
        return 0L;
    }

    private static long chargeForgeEnergyItem(long maxDischargeAmount, IElectricItem source, IEnergyStorage target) {
        long maxDischarged = source.discharge(maxDischargeAmount, source.getTier(), false, true, true);
        long received = FeCompat.insertEu(target, maxDischarged, false);
        if (received > 0L) {
            source.discharge(received, source.getTier(), false, true, false);
            return received;
        }
        return 0L;
    }

    private static void setInDischargeMode(ItemStack itemStack, boolean isDischargeMode) {
        var tagCompound = itemStack.getOrCreateTag();
        if (isDischargeMode) {
            tagCompound.putBoolean("DischargeMode", true);
        } else {
            tagCompound.remove("DischargeMode");
            if (tagCompound.isEmpty()) {
                itemStack.setTag(null);
            }
        }
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem == null) return;
        addCurrentChargeTooltip(tooltipComponents, electricItem.getCharge(), electricItem.getMaxCharge(),
                electricItem.getTier());
        addBatteryTooltip(tooltipComponents, stack, electricItem.getMaxCharge(), electricItem.getTier(), false);
        if (electricItem.canProvideChargeExternally()) {
            tooltipComponents.add(Tips.rightClick(Component.translatable("metaitem.electric.discharge_mode.tooltip")));
        }
    }

    public static void addCurrentChargeTooltip(List<Component> tooltip, long currentCharge, long maxCharge, int tier) {
        tooltip.add(Tips.charge(Math.max(0, currentCharge), Math.max(0, maxCharge), tier));
    }

    @Nullable
    private static Long2ObjectOpenHashMap<Item> batteries;

    public static void addBatteryTooltip(List<Component> tooltip, ItemStack stack, long maxCharge, int tier, boolean infer) {
        if (maxCharge <= 0) return;
        Item battery = IDataComponentItem.get(stack, ElectricItem.BATTERY);
        if (battery == null && infer) battery = findBattery(maxCharge, tier);
        if (battery != null) tooltip.add(Tips.stat(StatKind.BATTERY, battery.getDescription()));
    }

    @Nullable
    private static Item findBattery(long maxCharge, int tier) {
        if (batteries == null) {
            var map = new Long2ObjectOpenHashMap<Item>();
            var duplicated = new LongOpenHashSet();
            for (Item item : BuiltInRegistries.ITEM) {
                if (!(item instanceof IComponentItem componentItem)) continue;
                for (var component : componentItem.getComponents()) {
                    if (component instanceof ElectricStats stats && stats.dischargeable) {
                        long key = stats.maxCharge * 32 + stats.tier;
                        if (map.putIfAbsent(key, item) != null) duplicated.add(key);
                    }
                }
            }
            for (var it = duplicated.iterator(); it.hasNext();) map.remove(it.nextLong());
            batteries = map;
        }
        return batteries.get(maxCharge * 32 + tier);
    }

    private static boolean isInDischargeMode(ItemStack itemStack) {
        var tagCompound = itemStack.getTag();
        return tagCompound != null && tagCompound.getBoolean("DischargeMode");
    }

    public static ElectricStats createElectricItem(long maxCharge, int tier) {
        return ElectricStats.create(maxCharge, tier, true, false);
    }

    public static ElectricStats createRechargeableBattery(long maxCharge, int tier) {
        return ElectricStats.create(maxCharge, tier, true, true);
    }

    public static ElectricStats createBattery(long maxCharge, int tier, boolean rechargeable) {
        return ElectricStats.create(maxCharge, tier, rechargeable, true);
    }

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        var electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem != null) {
            return ToolChargeBarRenderer.renderElectricBar(guiGraphics, electricItem.getCharge(),
                    electricItem.getMaxCharge(), xOffset, yOffset, stack.isBarVisible());
        }
        return false;
    }
}
