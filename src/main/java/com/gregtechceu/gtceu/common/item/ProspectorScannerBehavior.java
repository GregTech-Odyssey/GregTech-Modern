package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.item.HeldItemPage;
import com.gregtechceu.gtceu.uiwidgets.prospector.ProspectorMapView;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ProspectorScannerBehavior implements IItemUIFactory, IAddInformation {

    private static final String DARK_MAP = "gtceu.prospector.ui.dark_map";
    private static final String LIGHT_MAP = "gtceu.prospector.ui.light_map";

    private final int radius;
    private final long cost;
    private final ProspectorMode<?>[] modes;

    public ProspectorScannerBehavior(int radius, long cost, ProspectorMode<?>... modes) {
        this.radius = radius + 1;
        this.modes = Arrays.stream(modes).filter(Objects::nonNull).toArray(ProspectorMode[]::new);
        this.cost = cost;
    }

    public int getModeIndex(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null ? 0 : Math.floorMod(tag.getInt("Mode"), modes.length);
    }

    @NotNull
    public ProspectorMode<?> getMode(ItemStack stack) {
        return modes[getModeIndex(stack)];
    }

    public void setMode(ItemStack stack, int index) {
        stack.getOrCreateTag().putInt("Mode", Math.floorMod(index, modes.length));
    }

    public boolean isDarkMap(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.getBoolean("DarkMap");
    }

    public void setDarkMap(ItemStack stack, boolean dark) {
        stack.getOrCreateTag().putBoolean("DarkMap", dark);
    }

    public boolean drainEnergy(@NotNull ItemStack stack, boolean simulate) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem == null) return false;

        int amount = Math.round(cost * (ConfigHolder.INSTANCE.machines.prospectorEnergyUseMultiplier / 100F));

        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack heldItem = player.getItemInHand(usedHand);
        if (!player.isCreative() && !drainEnergy(heldItem, true)) {
            player.sendSystemMessage(Component.translatable("behavior.prospector.not_enough_energy"));
            return InteractionResultHolder.success(heldItem);
        }
        return IItemUIFactory.super.use(item, level, player, usedHand);
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        var page = new HeldItemPage(holder, window -> new ProspectorMapView(window, radius, modes,
                () -> getModeIndex(held(holder)), () -> isDarkMap(held(holder))));
        var buttons = new IFancyConfigurator[modes.length > 1 ? modes.length + 1 : 1];
        buttons[0] = new IFancyConfiguratorButton.Toggle(WidgetIcons.MAP_LIGHT, WidgetIcons.MAP_DARK,
                () -> isDarkMap(held(holder)), (clickData, pressed) -> setDarkMap(held(holder), pressed))
                .setTooltipsSupplier(pressed -> Collections.singletonList(Component.translatable(pressed ? DARK_MAP : LIGHT_MAP)));
        if (modes.length > 1) {
            for (int i = 0; i < modes.length; i++) buttons[i + 1] = modeButton(holder, i);
        }
        return page.configurators(buttons).noInventory().noScroll().createUI(entityPlayer);
    }

    private IFancyConfigurator modeButton(HeldItemUIFactory.HeldItemHolder holder, int index) {
        var mode = modes[index];
        return new IFancyConfiguratorButton.Toggle(modeIcon(mode, false), modeIcon(mode, true),
                () -> getModeIndex(held(holder)) == index,
                (clickData, pressed) -> {
                    if (pressed) setMode(held(holder), index);
                })
                .setTooltipsSupplier(pressed -> Collections.singletonList(Component.translatable(mode.unlocalizedName)));
    }

    private static ItemStack held(HeldItemUIFactory.HeldItemHolder holder) {
        return holder.player.getItemInHand(holder.hand);
    }

    private static IGuiTexture modeIcon(ProspectorMode<?> mode, boolean on) {
        if (mode == ProspectorMode.FLUID) return on ? WidgetIcons.PROSPECT_FLUID_ON : WidgetIcons.PROSPECT_FLUID_OFF;
        if (mode == ProspectorMode.BEDROCK_ORE) return on ? WidgetIcons.PROSPECT_BEDROCK_ORE_ON : WidgetIcons.PROSPECT_BEDROCK_ORE_OFF;
        return on ? WidgetIcons.PROSPECT_ORE_ON : WidgetIcons.PROSPECT_ORE_OFF;
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("metaitem.prospector.tooltip.radius", radius));
        tooltipComponents.add(Component.translatable("metaitem.prospector.tooltip.modes"));
        for (ProspectorMode<?> mode : modes) {
            tooltipComponents.add(Component.literal(" -").append(Component.translatable(mode.unlocalizedName))
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
        }
    }
}
