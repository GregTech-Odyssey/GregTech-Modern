package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureIssue;
import com.gregtechceu.gtceu.uiwidgets.structure.StructureIssueOverlay;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class MultiblockStructureProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private static final String FORMED = "formed";
    private static final String ISSUE = "issue";
    private static final String ISSUE_POS = "issuePos";

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        var data = blockAccessor.getServerData();
        if (!data.contains(FORMED)) return;
        if (data.getBoolean(FORMED)) {
            iTooltip.add(Component.translatable("gtceu.top.valid_structure").withStyle(ChatFormatting.GREEN));
            return;
        }
        iTooltip.add(Component.translatable("gtceu.top.invalid_structure").withStyle(ChatFormatting.RED));
        for (var line : data.getList(ISSUE, Tag.TAG_STRING)) {
            var component = Component.Serializer.fromJson(line.getAsString());
            if (component != null) iTooltip.add(component.copy().withStyle(ChatFormatting.GRAY));
        }
        if (data.contains(ISSUE_POS)) StructureIssueOverlay.show(blockAccessor.getPosition(), data.getLong(ISSUE_POS));
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity &&
                blockEntity.getMetaMachine() instanceof IMultiController controller) {
            boolean formed = controller.isFormed();
            compoundTag.putBoolean(FORMED, formed);
            if (formed) return;
            var issue = controller.getMultiblockState().getReportedIssue();
            var lines = new ListTag();
            for (var line : StructureIssue.lines(issue)) lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));
            compoundTag.put(ISSUE, lines);
            if (issue != null && issue.getPos() != null) compoundTag.putLong(ISSUE_POS, issue.getPosLong());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return GTCEu.id("multiblock_structure");
    }
}
