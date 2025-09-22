package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.WeightedMaterial;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.trait.BedrockOreMinerLogic;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BedrockOreMinerMachine extends WorkableElectricMultiblockMachine {

    private final int t;

    public BedrockOreMinerMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder);
        this.t = tier;
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new BedrockOreMinerLogic(this);
    }

    @Override
    public @NotNull BedrockOreMinerLogic getRecipeLogic() {
        return (BedrockOreMinerLogic) super.getRecipeLogic();
    }

    public int getEnergyTier() {
        return Math.min(this.t + 1, super.tier);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        if (isFormed()) {
            int energyContainer = getEnergyTier();
            long maxVoltage = GTValues.V[energyContainer];
            String voltageName = GTValues.VNF[energyContainer];
            textList.add(Component.translatable("gtceu.multiblock.max_energy_per_tick", maxVoltage, voltageName));
            if (getRecipeLogic().getVeinMaterials() != null) {
                // Ore names
                textList.add(Component.translatable("gtceu.multiblock.ore_rig.drilled_ores_list").withStyle(ChatFormatting.GREEN));
                List<WeightedMaterial> drilledOres = getRecipeLogic().getVeinMaterials();
                for (var entry : drilledOres) {
                    Component fluidInfo = entry.material().getLocalizedName().withStyle(ChatFormatting.GREEN);
                    textList.add(Component.translatable("gtceu.multiblock.ore_rig.drilled_ore_entry", fluidInfo).withStyle(ChatFormatting.GRAY));
                }
                // Ore amount
                Component amountInfo = Component.literal(FormattingUtil.formatNumbers(getRecipeLogic().getOreToProduce() * 20L / BedrockOreMinerLogic.MAX_PROGRESS) + "/s").withStyle(ChatFormatting.BLUE);
                textList.add(Component.translatable("gtceu.multiblock.ore_rig.ore_amount", amountInfo).withStyle(ChatFormatting.GRAY));
            } else {
                Component noOre = Component.translatable("gtceu.multiblock.fluid_rig.no_fluid_in_area").withStyle(ChatFormatting.RED);
                textList.add(Component.translatable("gtceu.multiblock.ore_rig.drilled_ores_list").withStyle(ChatFormatting.GREEN));
                textList.add(Component.translatable("gtceu.multiblock.ore_rig.drilled_ore_entry", noOre).withStyle(ChatFormatting.GRAY));
            }
        } else {
            Component tooltip = Component.translatable("gtceu.multiblock.invalid_structure.tooltip").withStyle(ChatFormatting.GRAY);
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))));
        }
    }

    public static int getDepletionChance(int tier) {
        if (tier == GTValues.MV) return 1;
        if (tier == GTValues.HV) return 2;
        if (tier == GTValues.EV) return 8;
        return 1;
    }

    public static int getRigMultiplier(int tier) {
        if (tier == GTValues.MV) return 1;
        if (tier == GTValues.HV) return 4;
        if (tier == GTValues.EV) return 16;
        return 1;
    }

    public static Block getCasingState(int tier) {
        if (tier == GTValues.MV) return GTBlocks.CASING_STEEL_SOLID.get();
        if (tier == GTValues.HV) return GTBlocks.CASING_TITANIUM_STABLE.get();
        if (tier == GTValues.EV) return GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get();
        return GTBlocks.CASING_STEEL_SOLID.get();
    }

    public static Block getFrameState(int tier) {
        if (tier == GTValues.MV) return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Steel).get();
        if (tier == GTValues.HV) return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Titanium).get();
        if (tier == GTValues.EV) return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.TungstenSteel).get();
        return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Steel).get();
    }

    public static ResourceLocation getBaseTexture(int tier) {
        if (tier == GTValues.MV) return GTCEu.id("block/casings/solid/machine_casing_solid_steel");
        if (tier == GTValues.HV) return GTCEu.id("block/casings/solid/machine_casing_stable_titanium");
        if (tier == GTValues.EV) return GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel");
        return GTCEu.id("block/casings/solid/machine_casing_solid_steel");
    }

    public int getTier() {
        return this.t;
    }
}
