package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.client.util.TooltipHelper;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import com.gregtechceu.gtceu.integration.jade.IssueJade;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class RecipeLogicProvider extends CapabilityBlockProvider<RecipeLogic> {

    public static final ResourceLocation UID = GTCEu.id("recipe_logic_provider");

    public RecipeLogicProvider() {
        super(UID);
    }

    @Nullable
    @Override
    protected RecipeLogic getCapability(Level level, BlockPos pos, BlockEntity blockEntity, @Nullable Direction side) {
        return GTCapabilityHelper.getRecipeLogic(blockEntity);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        var capability = getCapability(blockAccessor.getLevel(), blockAccessor.getPosition(), blockAccessor.getBlockEntity(), null);
        if (capability == null) return;
        var tag = new CompoundTag();
        write(tag, capability);
        var capData = data.getCompound(UID.toString());
        capData.put("null", tag);
        data.put(UID.toString(), capData);
    }

    @Override
    protected void write(CompoundTag data, RecipeLogic capability) {
        data.putBoolean("Working", capability.isWorking());
        IssueJade.write(data, capability);
        var recipeInfo = new CompoundTag();
        var recipe = capability.getLastRecipe();
        if (recipe != null) {
            long EUt = recipe.getInputEUt();
            boolean isInput = true;
            if (EUt == 0) {
                isInput = false;
                EUt = recipe.getOutputEUt();
            }

            recipeInfo.putLong("EUt", EUt);
            recipeInfo.putBoolean("isInput", isInput);
        }

        if (!recipeInfo.isEmpty()) {
            data.put("Recipe", recipeInfo);
        }
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block,
                              BlockEntity blockEntity, IPluginConfig config) {
        IssueJade.append(tooltip, capData, block.showDetails());
        if (capData.getBoolean("Working")) {
            var recipeInfo = capData.getCompound("Recipe");
            if (!recipeInfo.isEmpty()) {
                var EUt = recipeInfo.getLong("EUt");
                var isInput = recipeInfo.getBoolean("isInput");
                boolean isSteam = false;

                if (blockEntity instanceof MetaMachineBlockEntity mbe) {
                    var machine = mbe.getMetaMachine();
                    if (machine instanceof SimpleSteamMachine ssm) {
                        EUt = (long) (EUt * ssm.getConversionRate());
                        isSteam = true;
                    } else if (machine instanceof SteamParallelMultiblockMachine smb) {
                        EUt = (long) (EUt * smb.getConversionRate());
                        isSteam = true;
                    }
                }

                if (EUt > 0) {
                    MutableComponent text;

                    if (isSteam) {
                        text = Component.literal(FormattingUtil.formatNumbers(EUt)).withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(" mB/t").withStyle(ChatFormatting.RESET));
                    } else {
                        var tier = GTUtil.getOCTierByVoltage(EUt);

                        text = Component.literal(FormattingUtil.formatNumbers(EUt)).withStyle(ChatFormatting.RED)
                                .append(Component.literal(" EU/t").withStyle(ChatFormatting.RESET)
                                        .append(Component.literal(" (").withStyle(ChatFormatting.GREEN)));
                        if (tier < GTValues.TIER_COUNT) {
                            text = text.append(Component.literal(GTValues.VNF[tier])
                                    .withStyle(style -> style.withColor(GTValues.VC[tier])));
                        } else {
                            int speed = tier - 14;
                            text = text.append(Component
                                    .literal("MAX")
                                    .withStyle(style -> style.withColor(TooltipHelper.rainbowColor(speed)))
                                    .append(Component.literal("+")
                                            .withStyle(style -> style.withColor(GTValues.VC[speed]))
                                            .append(Component.literal(FormattingUtil.formatNumbers(tier - 14)))
                                            .withStyle(style -> style.withColor(GTValues.VC[speed]))));

                        }
                        text = text.append(Component.literal(")").withStyle(ChatFormatting.GREEN));
                    }

                    if (isInput) {
                        tooltip.add(Component.translatable("gtceu.top.energy_consumption").append(" ").append(text));
                    } else {
                        tooltip.add(Component.translatable("gtceu.top.energy_production").append(" ").append(text));
                    }
                }
            }
        }
    }
}
