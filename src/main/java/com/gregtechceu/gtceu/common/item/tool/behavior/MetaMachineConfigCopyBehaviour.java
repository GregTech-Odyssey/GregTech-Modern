package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ConfigCopySupport;
import com.gregtechceu.gtceu.api.machine.feature.IConfigCopyable;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Machine memory card. The card is a plain NBT container plus the interaction: it knows nothing
 * about individual settings and talks to machines through {@link IConfigCopyable} only, so adding a
 * copyable setting never requires touching this class.
 * <p>
 * The one thing the card owns is the source machine's front facing, which is global metadata rather
 * than any single machine's setting — machines that store sides relative to their front read it back
 * out of the same tag when pasting.
 */
public class MetaMachineConfigCopyBehaviour implements IInteractionItem, IAddInformation {

    public static final String CONFIG_DATA = "config_data";

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        var stack = player.getItemInHand(usedHand);
        if (player.isShiftKeyDown()) {
            stack.removeTagKey(CONFIG_DATA);
            return InteractionResultHolder.success(stack);
        }
        return IInteractionItem.super.use(item, level, player, usedHand);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // spotless:off
        // Copy and paste must run server-side only: not every copyable setting is synced to the
        // client (a machine's circuit slot, for one), so a client-side copy would read a blank value
        // and a client-side paste would write one back.
        boolean clientSide = context.getLevel().isClientSide;
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MetaMachineBlockEntity blockEntity) {
            var machine = blockEntity.getMetaMachine();
            if (!MachineOwner.canOpenOwnerMachine(context.getPlayer(), machine)) {
                return InteractionResult.FAIL;
            }
            if (context.isSecondaryUseActive()) {
                return clientSide ? InteractionResult.SUCCESS : handleCopy(stack, machine);
            } else if (stack.getTagElement(CONFIG_DATA) != null) {
                return clientSide ? InteractionResult.SUCCESS : handlePaste(stack, machine);
            }
        } else if (context.isSecondaryUseActive() && context.getLevel().getBlockState(context.getClickedPos()).isAir()) {
            // Clearing only touches the item's own NBT, so both sides can do it symmetrically.
            stack.removeTagKey(CONFIG_DATA);
            return InteractionResult.SUCCESS;
        }
        // spotless:on
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleCopy(ItemStack stack, MetaMachine machine) {
        CompoundTag configData = new CompoundTag();
        if (machine instanceof IConfigCopyable copyable) {
            configData.putString(ConfigCopySupport.ORIGINAL_FRONT,
                    ConfigCopySupport.directionToString(machine.getFrontFacing()));
            copyable.writeConfigTo(configData);
        }
        // Copying means "the card now holds THIS machine". A machine that contributed nothing
        // (only the front facing, or not copyable at all) therefore clears the card instead of
        // silently keeping a previous machine's config around to be pasted later.
        if (configData.size() > 1) {
            stack.getOrCreateTag().put(CONFIG_DATA, configData);
        } else {
            stack.removeTagKey(CONFIG_DATA);
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handlePaste(ItemStack stack, MetaMachine machine) {
        CompoundTag configData = stack.getTagElement(CONFIG_DATA);
        if (configData == null) return InteractionResult.PASS;
        if (machine instanceof IConfigCopyable copyable) {
            copyable.readConfigFrom(configData);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("behaviour.meta.machine.config.copy.tooltip"));
        tooltipComponents.add(Component.translatable("behaviour.meta.machine.config.paste.tooltip"));
        CompoundTag data = stack.getTagElement(CONFIG_DATA);
        if (data == null) return;
        if (Screen.hasShiftDown()) {
            tooltipComponents.add(CommonComponents.EMPTY);
            ConfigCopySupport.appendStoredTooltips(data, tooltipComponents);
        } else {
            tooltipComponents.add(Component.translatable("item.toggle.advanced.info.tooltip"));
        }
    }
}
