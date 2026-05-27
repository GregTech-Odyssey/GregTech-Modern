package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IDataItem;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.fast.fastcollection.OpenCacheHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class DataItemBehavior implements IInteractionItem, IAddInformation, IDataItem {

    private final boolean requireDataBank;

    public DataItemBehavior() {
        this.requireDataBank = false;
    }

    public DataItemBehavior(boolean requireDataBank) {
        this.requireDataBank = requireDataBank;
    }

    @Override
    public boolean requireDataBank() {
        return requireDataBank;
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        ResearchManager.ResearchItem researchData = ResearchManager.readResearchId(stack);
        if (researchData == null) {
            if (stack.getOrCreateTag().contains("pos", Tag.TAG_INT_ARRAY) && stack.hasTag()) {
                int[] posArray = stack.getOrCreateTag().getIntArray("pos");
                tooltipComponents.add(Component.translatable(
                        "gtceu.tooltip.proxy_bind",
                        Component.literal("" + posArray[0]).withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.literal("" + posArray[1]).withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.literal("" + posArray[2]).withStyle(ChatFormatting.LIGHT_PURPLE)));
            }
        } else {
            Collection<GTRecipeDefinition> recipes = researchData.recipeType().getDataStickEntry(researchData.researchId());
            if (recipes != null && !recipes.isEmpty()) {
                tooltipComponents.add(Component.translatable("behavior.data_item.assemblyline.title"));
                Collection<ItemStack> added = new OpenCacheHashSet<>();
                outer:
                for (GTRecipeDefinition recipe : recipes) {
                    ItemStack output = recipe.itemOutputs.getFirst().inner.getInnerItemStack();
                    for (var item : added) {
                        if (output.is(item.getItem())) continue outer;
                    }
                    if (added.add(output)) {
                        tooltipComponents.add(
                                Component.translatable("behavior.data_item.assemblyline.data",
                                        output.getDisplayName()));
                    }
                }
            }
        }
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MetaMachineBlockEntity blockEntity) {
            var machine = blockEntity.getMetaMachine();
            if (!MachineOwner.canOpenOwnerMachine(context.getPlayer(), machine)) {
                return InteractionResult.FAIL;
            }
            if (machine instanceof IDataStickInteractable interactable) {
                if (context.isSecondaryUseActive()) {
                    if (ResearchManager.readResearchId(itemStack) == null) {
                        return interactable.onDataStickShiftUse(context.getPlayer(), itemStack);
                    }
                    return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
                } else {
                    return interactable.onDataStickUse(context.getPlayer(), itemStack);
                }
            }
        }
        return InteractionResult.PASS;
    }
}
