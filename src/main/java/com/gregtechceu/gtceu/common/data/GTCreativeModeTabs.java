package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.common.pipelike.cable.Insulation;

import net.minecraft.world.item.*;

import com.tterrag.registrate.util.entry.RegistryEntry;

import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

@SuppressWarnings("Convert2MethodRef")
public class GTCreativeModeTabs {

    public static RegistryEntry<CreativeModeTab> MATERIAL_FLUID = REGISTRATE.defaultCreativeTab("material_fluid",
            builder -> builder.icon(() -> GTItems.FLUID_CELL.asStack())
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("material_fluid"),
                            GTCEu.NAME + " Material Fluid Containers"))
                    .build())
            .register();
    public static RegistryEntry<CreativeModeTab> MATERIAL_ITEM = REGISTRATE.defaultCreativeTab("material_item",
            builder -> builder
                    .icon(() -> ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Aluminium))
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("material_item"), GTCEu.NAME + " Material Items"))
                    .build())
            .register();
    public static RegistryEntry<CreativeModeTab> MATERIAL_BLOCK = REGISTRATE.defaultCreativeTab("material_block",
            builder -> builder
                    .icon(() -> ChemicalHelper.get(TagPrefix.block, GTMaterials.Gold))
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("material_block"), GTCEu.NAME + " Material Blocks"))
                    .build())
            .register();
    public static RegistryEntry<CreativeModeTab> MATERIAL_PIPE = REGISTRATE.defaultCreativeTab("material_pipe",
            builder -> builder
                    .icon(() -> ChemicalHelper.get(Insulation.WIRE_DOUBLE.getTagPrefix(), GTMaterials.Copper))
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("material_pipe"), GTCEu.NAME + " Material Pipes"))
                    .build())
            .register();
    public static RegistryEntry<CreativeModeTab> DECORATION = REGISTRATE.defaultCreativeTab("decoration",
            builder -> builder
                    .icon(() -> GTBlocks.COIL_CUPRONICKEL.asStack())
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("decoration"), GTCEu.NAME + " Decoration Blocks"))
                    .build())
            .register();
    public static RegistryEntry<CreativeModeTab> TOOL = REGISTRATE.defaultCreativeTab("tool",
            builder -> builder
                    .icon(() -> ToolHelper.get(GTToolType.WRENCH, GTMaterials.Steel))
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("tool"), GTCEu.NAME + " Tools"))
                    .build())
            .register();
    public static RegistryEntry<CreativeModeTab> MACHINE = REGISTRATE.defaultCreativeTab("machine",
            builder -> builder
                    .icon(() -> GTMachines.ELECTROLYZER[GTValues.LV].asStack())
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("machine"), GTCEu.NAME + " Machines"))
                    .build())
            .register();
    public static RegistryEntry<CreativeModeTab> ITEM = REGISTRATE.defaultCreativeTab("item",
            builder -> builder
                    .icon(() -> GTItems.BASIC_TAPE.asStack())
                    .title(REGISTRATE.addLang("itemGroup", GTCEu.id("item"), GTCEu.NAME + " Items"))
                    .build())
            .register();

    public static void init() {}
}
