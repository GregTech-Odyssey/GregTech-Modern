package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.content.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.integration.jade.GTElementHelper;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.util.FluidTextHelper;

import java.util.ArrayList;
import java.util.List;

public class RecipeOutputProvider extends CapabilityBlockProvider<RecipeLogic> {

    private static final String AMOUNT = "LongAmount";

    public RecipeOutputProvider() {
        super(GTCEu.id("recipe_output_info"));
    }

    @Override
    protected @Nullable RecipeLogic getCapability(Level level, BlockPos pos, BlockEntity blockEntity, @Nullable Direction side) {
        return GTCapabilityHelper.getRecipeLogic(blockEntity);
    }

    @Override
    protected void write(CompoundTag data, RecipeLogic recipeLogic) {
        if (recipeLogic.isWorking()) {
            data.putBoolean("Working", recipeLogic.isWorking());
            var recipe = recipeLogic.getLastRecipe();
            if (recipe != null) {
                int recipeTier = recipe.tier;
                int chanceTier = recipeTier + recipe.ocLevel;
                var function = recipe.definition.chanceFunction;
                var itemContents = recipe.itemOutputs;
                var fluidContents = recipe.fluidOutputs;

                ListTag itemTags = new ListTag();
                for (int i = 0; i < itemContents.size(); i++) {
                    if (!(itemContents.ingredient(i).displayKey() instanceof AEItemKey key)) continue;
                    var itemTag = new CompoundTag();
                    GTUtil.saveItemStack(Keys.displayStack(key), itemTag);
                    itemTag.putLong(AMOUNT, outputAmount(itemContents, i, recipe.scale, function, recipeTier, chanceTier));
                    itemTags.add(itemTag);
                }

                if (!itemTags.isEmpty()) {
                    data.put("OutputItems", itemTags);
                }

                ListTag fluidTags = new ListTag();
                for (int i = 0; i < fluidContents.size(); i++) {
                    if (!(fluidContents.ingredient(i).displayKey() instanceof AEFluidKey key)) continue;
                    var fluidTag = new CompoundTag();
                    key.toStack(1).writeToNBT(fluidTag);
                    fluidTag.putLong(AMOUNT, outputAmount(fluidContents, i, recipe.scale, function, recipeTier, chanceTier));
                    fluidTags.add(fluidTag);
                }

                if (!fluidTags.isEmpty()) {
                    data.put("OutputFluids", fluidTags);
                }
            }
        }
    }

    private static long outputAmount(ContentList contents, int i, long scale, ChanceBoostFunction function, int recipeTier, int chanceTier) {
        long amount = contents.effective(i, scale);
        int chance = contents.chance(i);
        if (chance >= ContentList.MAX_CHANCE) return amount;
        double expected = (double) amount * function.getBoostedChance(chance, contents.boost(i), recipeTier, chanceTier) / ContentList.MAX_CHANCE;
        return expected < 1 ? 1 : Math.round(expected);
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block,
                              BlockEntity blockEntity, IPluginConfig config) {
        if (capData.getBoolean("Working")) {
            List<ItemStack> outputItems = new ArrayList<>();
            LongList itemAmounts = new LongArrayList();
            if (capData.contains("OutputItems", Tag.TAG_LIST)) {
                ListTag itemTags = capData.getList("OutputItems", Tag.TAG_COMPOUND);
                if (!itemTags.isEmpty()) {
                    for (Tag tag : itemTags) {
                        if (tag instanceof CompoundTag tCompoundTag) {
                            var stack = GTUtil.loadItemStack(tCompoundTag);
                            long amount = tCompoundTag.getLong(AMOUNT);
                            if (!stack.isEmpty() && amount > 0) {
                                outputItems.add(stack);
                                itemAmounts.add(amount);
                            }
                        }
                    }
                }
            }
            List<FluidStack> outputFluids = new ArrayList<>();
            LongList fluidAmounts = new LongArrayList();
            if (capData.contains("OutputFluids", Tag.TAG_LIST)) {
                ListTag fluidTags = capData.getList("OutputFluids", Tag.TAG_COMPOUND);
                for (Tag tag : fluidTags) {
                    if (tag instanceof CompoundTag tCompoundTag) {
                        var stack = FluidStack.loadFluidStackFromNBT(tCompoundTag);
                        long amount = tCompoundTag.getLong(AMOUNT);
                        if (!stack.isEmpty() && amount > 0) {
                            outputFluids.add(stack);
                            fluidAmounts.add(amount);
                        }
                    }
                }
            }
            if (!outputItems.isEmpty() || !outputFluids.isEmpty()) {
                tooltip.add(Component.translatable("gtceu.top.recipe_output"));
            }
            addItemTooltips(tooltip, outputItems, itemAmounts);
            addFluidTooltips(tooltip, outputFluids, fluidAmounts);
        }
    }

    private void addItemTooltips(ITooltip iTooltip, List<ItemStack> outputItems, LongList amounts) {
        IElementHelper helper = iTooltip.getElementHelper();
        for (int i = 0; i < outputItems.size(); i++) {
            ItemStack itemOutput = outputItems.get(i);
            itemOutput.setCount(1);
            iTooltip.add(helper.smallItem(itemOutput));
            Component text = Component.literal(" ")
                    .append(FormattingUtil.formatNumbers(amounts.getLong(i)))
                    .append("× ")
                    .append(getItemName(itemOutput))
                    .withStyle(ChatFormatting.WHITE);
            iTooltip.append(text);
        }
    }

    private void addFluidTooltips(ITooltip iTooltip, List<FluidStack> outputFluids, LongList amounts) {
        for (int i = 0; i < outputFluids.size(); i++) {
            FluidStack fluidOutput = outputFluids.get(i);
            long amount = amounts.getLong(i);
            iTooltip.add(GTElementHelper.smallFluid(JadeFluidObject.of(fluidOutput.getFluid(), amount)));
            Component text = Component.literal(" ")
                    .append(FluidTextHelper.getUnicodeMillibuckets(amount, true))
                    .append(" ")
                    .append(getFluidName(fluidOutput))
                    .withStyle(ChatFormatting.WHITE);
            iTooltip.append(text);
        }
    }

    private Component getItemName(ItemStack stack) {
        return ComponentUtils.wrapInSquareBrackets(stack.getItem().getDescription()).withStyle(ChatFormatting.WHITE);
    }

    private Component getFluidName(FluidStack stack) {
        return ComponentUtils.wrapInSquareBrackets(stack.getDisplayName()).withStyle(ChatFormatting.WHITE);
    }
}
