package com.gregtechceu.gtceu.common.data.machines;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.fluids.PropertyFluidFilter;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Piece;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Slot;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Symbols;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.client.renderer.machine.*;
import com.gregtechceu.gtceu.client.util.TooltipHelper;
import com.gregtechceu.gtceu.common.block.BoilerFireboxType;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.*;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CharcoalPileIgniterMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveBlastFurnaceMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitivePumpMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;

import appeng.api.networking.pathing.ChannelMode;
import appeng.core.AEConfig;

import java.util.Locale;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;
import static com.gregtechceu.gtceu.common.data.GTMachines.*;
import static com.gregtechceu.gtceu.common.data.GTMaterials.DrillingFluid;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.DUMMY_RECIPES;
import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;
import static com.gregtechceu.gtceu.utils.FormattingUtil.toRomanNumeral;

public class GTMultiMachines {

    //////////////////////////////////////
    // ******* Multiblock *******//
    //////////////////////////////////////
    public static final MultiblockMachineDefinition LARGE_BOILER_BRONZE = registerLargeBoiler("bronze",
            CASING_BRONZE_BRICKS, CASING_BRONZE_PIPE, FIREBOX_BRONZE,
            GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"), BoilerFireboxType.BRONZE_FIREBOX,
            ConfigHolder.INSTANCE.machines.largeBoilers.bronzeBoilerMaxTemperature,
            ConfigHolder.INSTANCE.machines.largeBoilers.bronzeBoilerHeatSpeed);
    public static final MultiblockMachineDefinition LARGE_BOILER_STEEL = registerLargeBoiler("steel",
            CASING_STEEL_SOLID, CASING_STEEL_PIPE, FIREBOX_STEEL,
            GTCEu.id("block/casings/solid/machine_casing_solid_steel"), BoilerFireboxType.STEEL_FIREBOX,
            ConfigHolder.INSTANCE.machines.largeBoilers.steelBoilerMaxTemperature,
            ConfigHolder.INSTANCE.machines.largeBoilers.steelBoilerHeatSpeed);
    public static final MultiblockMachineDefinition LARGE_BOILER_TITANIUM = registerLargeBoiler("titanium",
            CASING_TITANIUM_STABLE, CASING_TITANIUM_PIPE, FIREBOX_TITANIUM,
            GTCEu.id("block/casings/solid/machine_casing_stable_titanium"), BoilerFireboxType.TITANIUM_FIREBOX,
            ConfigHolder.INSTANCE.machines.largeBoilers.titaniumBoilerMaxTemperature,
            ConfigHolder.INSTANCE.machines.largeBoilers.titaniumBoilerHeatSpeed);
    public static final MultiblockMachineDefinition LARGE_BOILER_TUNGSTENSTEEL = registerLargeBoiler("tungstensteel",
            CASING_TUNGSTENSTEEL_ROBUST, CASING_TUNGSTENSTEEL_PIPE, FIREBOX_TUNGSTENSTEEL,
            GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
            BoilerFireboxType.TUNGSTENSTEEL_FIREBOX,
            ConfigHolder.INSTANCE.machines.largeBoilers.tungstensteelBoilerMaxTemperature,
            ConfigHolder.INSTANCE.machines.largeBoilers.tungstensteelBoilerHeatSpeed);

    public static final MultiblockMachineDefinition COKE_OVEN = REGISTRATE.multiblock("coke_oven", CokeOvenMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.COKE_OVEN_RECIPES)
            .appearanceBlock(CASING_COKE_BRICKS)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .wherePart('X',
                                blocks(CASING_COKE_BRICKS.get()).or(blocks(COKE_OVEN_HATCH.get()).setMaxGlobalLimited(5)))
                        .where('#', Predicates.air())
                        .where('Y', Predicates.controller(definition));
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "X#X", "XXX")
                        .aisle("XXX", "XYX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_coke_bricks"),
                    GTCEu.id("block/multiblock/coke_oven"))
            .register();

    public static final MultiblockMachineDefinition PRIMITIVE_BLAST_FURNACE = REGISTRATE
            .multiblock("primitive_blast_furnace", PrimitiveBlastFurnaceMachine::new)
            .nonYAxisRotation()
            .recipeType(GTRecipeTypes.PRIMITIVE_BLAST_FURNACE_RECIPES)
            .renderer(() -> new PrimitiveBlastFurnaceRenderer(GTCEu.id("block/casings/solid/machine_primitive_bricks"),
                    GTCEu.id("block/multiblock/primitive_blast_furnace")))
            .hasTESR(true)
            .appearanceBlock(CASING_PRIMITIVE_BRICKS)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .wherePart('X', blocks(CASING_PRIMITIVE_BRICKS.get()))
                        .where('#', Predicates.air())
                        .where('Y', Predicates.controller(definition));
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XXX", "XXX", "XXX")
                        .aisle("XXX", "X#X", "X#X", "X#X")
                        .aisle("XXX", "XYX", "XXX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .register();

    public static final MultiblockMachineDefinition ELECTRIC_BLAST_FURNACE = REGISTRATE
            .multiblock("electric_blast_furnace", CoilWorkableElectricMultiblockMachine::new)
            .nonYAxisRotation()
            .recipeType(GTRecipeTypes.BLAST_RECIPES)
            .recipeModifiers(RecipeModifier.EBF_OVERCLOCK)
            .appearanceBlock(CASING_INVAR_HEATPROOF)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', controller(definition))
                        .wherePart('X', blocks(CASING_INVAR_HEATPROOF.get()).setMinGlobalLimited(9)
                                .or(autoAbilities(definition.getRecipeTypes()))
                                .or(autoAbilities(true, false, false)))
                        .where('M', abilities(PartAbility.MUFFLER))
                        .where('C', heatingCoils())
                        .where('#', air());
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "CCC", "CCC", "XXX")
                        .aisle("XXX", "C#C", "C#C", "XMX")
                        .aisle("XSX", "CCC", "CCC", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .recoveryStaticItems(() -> GTMaterialItems.MATERIAL_ITEMS.get(TagPrefix.dustTiny, GTMaterials.Ash).get())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_heatproof"),
                    GTCEu.id("block/multiblock/electric_blast_furnace"))
            .tooltips(Component.translatable("gtceu.machine.electric_blast_furnace.tooltip.0"),
                    Component.translatable("gtceu.machine.electric_blast_furnace.tooltip.1"),
                    Component.translatable("gtceu.machine.electric_blast_furnace.tooltip.2"))
            .additionalDisplay((controller, components) -> {
                if (controller instanceof CoilWorkableElectricMultiblockMachine coilMachine && controller.isFormed()) {
                    components.add(Component.translatable("gtceu.multiblock.blast_furnace.max_temperature",
                            Component
                                    .translatable(
                                            FormattingUtil
                                                    .formatNumbers(coilMachine.getCoilType().getCoilTemperature() +
                                                            100L * Math.max(0, coilMachine.getTier() - GTValues.MV)) +
                                                    "K")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED))));
                }
            })
            .register();

    public static final MultiblockMachineDefinition LARGE_CHEMICAL_REACTOR = REGISTRATE
            .multiblock("large_chemical_reactor", WorkableElectricMultiblockMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.LARGE_CHEMICAL_RECIPES)
            .recipeModifiers(RecipeModifier.PERFECT_OVERCLOCKING)
            .appearanceBlock(CASING_PTFE_INERT)
            .structure(definition -> {
                var casing = blocks(CASING_PTFE_INERT.get()).setMinGlobalLimited(10);
                var abilities = Predicates.autoAbilities(definition.getRecipeTypes())
                        .or(Predicates.autoAbilities(true, false, false));
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .wherePart('X', casing.or(abilities))
                        .where('P', blocks(CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()))
                        .where('C', Predicates.heatingCoils().setExactLimit(1)
                                .or(abilities)
                                .or(casing));
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XCX", "XXX")
                        .aisle("XCX", "CPC", "XCX")
                        .aisle("XXX", "XSX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_inert_ptfe"),
                    GTCEu.id("block/multiblock/large_chemical_reactor"))
            .register();

    public static final MultiblockMachineDefinition IMPLOSION_COMPRESSOR = REGISTRATE
            .multiblock("implosion_compressor", WorkableElectricMultiblockMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.IMPLOSION_RECIPES)
            .recipeModifiers(RecipeModifier.OVERCLOCKING)
            .appearanceBlock(CASING_STEEL_SOLID)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', controller(definition))
                        .wherePart('X', blocks(CASING_STEEL_SOLID.get()).setMinGlobalLimited(14)
                                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                                .or(Predicates.autoAbilities(true, true, false)))
                        .where('#', Predicates.air());
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "X#X", "XXX")
                        .aisle("XXX", "XSX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTCEu.id("block/multiblock/implosion_compressor"))
            .register();

    public static final MultiblockMachineDefinition PYROLYSE_OVEN = REGISTRATE
            .multiblock("pyrolyse_oven", CoilWorkableElectricMultiblockMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.PYROLYSE_RECIPES)
            .recipeModifiers(RecipeModifier.PYROLYSE_OVEN_OVERCLOCK)
            .appearanceBlock(MACHINE_CASING_ULV)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .wherePart('X',
                                blocks(MACHINE_CASING_ULV.get()).setMinGlobalLimited(6)
                                        .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                                        .or(Predicates.autoAbilities(true, true, false)))
                        .where('C', Predicates.heatingCoils())
                        .where('#', Predicates.air());
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("CCC", "C#C", "CCC")
                        .aisle("CCC", "C#C", "CCC")
                        .aisle("XXX", "XSX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/voltage/ulv/side"),
                    GTCEu.id("block/multiblock/pyrolyse_oven"))
            .tooltips(Component.translatable("gtceu.machine.pyrolyse_oven.tooltip.1"))
            .additionalDisplay((controller, components) -> {
                if (controller instanceof CoilWorkableElectricMultiblockMachine coilMachine && controller.isFormed()) {
                    components.add(Component.translatable("gtceu.multiblock.pyrolyse_oven.speed",
                            coilMachine.getCoilTier() == 0 ? 75 : 50 * (coilMachine.getCoilTier() + 1)));
                }
            })
            .register();

    public static final MultiblockMachineDefinition MULTI_SMELTER = REGISTRATE
            .multiblock("multi_smelter", CoilWorkableElectricMultiblockMachine::new)
            .nonYAxisRotation()
            .recipeTypes(GTRecipeTypes.FURNACE_RECIPES, GTRecipeTypes.ALLOY_SMELTER_RECIPES)
            .recipeModifiers(RecipeModifier.MULTI_SMELTER_OVERCLOCK)
            .appearanceBlock(CASING_INVAR_HEATPROOF)
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_2.tooltip",
                    Component.translatable("gtceu.electric_furnace"), Component.translatable("gtceu.alloy_smelter")))
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', controller(definition))
                        .wherePart('X', blocks(CASING_INVAR_HEATPROOF.get()).setMinGlobalLimited(9)
                                .or(autoAbilities(definition.getRecipeTypes()))
                                .or(autoAbilities(true, false, false)))
                        .where('M', abilities(PartAbility.MUFFLER))
                        .where('C', heatingCoils())
                        .where('#', air());
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "CCC", "XXX")
                        .aisle("XXX", "C#C", "XMX")
                        .aisle("XSX", "CCC", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .recoveryStaticItems(
                    () -> GTMaterialItems.MATERIAL_ITEMS.get(TagPrefix.dustTiny, GTMaterials.Ash).get())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_heatproof"),
                    GTCEu.id("block/multiblock/multi_furnace"))
            .additionalDisplay((controller, components) -> {
                if (controller instanceof CoilWorkableElectricMultiblockMachine coilMachine && controller.isFormed()) {
                    components.add(Component.translatable("gtceu.multiblock.multi_furnace.heating_coil_level",
                            coilMachine.getCoilType().getLevel()));
                    components.add(Component.translatable("gtceu.multiblock.multi_furnace.heating_coil_discount",
                            coilMachine.getCoilType().getEnergyDiscount()));
                }
            })
            .register();

    public static final MultiblockMachineDefinition CRACKER = REGISTRATE
            .multiblock("cracker", CoilWorkableElectricMultiblockMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.CRACKING_RECIPES)
            .recipeModifiers(RecipeModifier.CRACKER_OVERCLOCK)
            .appearanceBlock(CASING_STAINLESS_CLEAN)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('O', Predicates.controller(definition))
                        .wherePart('H', blocks(CASING_STAINLESS_CLEAN.get()).setMinGlobalLimited(12)
                                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                                .or(Predicates.autoAbilities(true, true, false)))
                        .where('#', Predicates.air())
                        .where('C', Predicates.heatingCoils());
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("HCHCH", "HCHCH", "HCHCH")
                        .aisle("HCHCH", "H###H", "HCHCH")
                        .aisle("HCHCH", "HCOCH", "HCHCH")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_clean_stainless_steel"),
                    GTCEu.id("block/multiblock/cracking_unit"))
            .tooltips(Component.translatable("gtceu.machine.cracker.tooltip.1"))
            .additionalDisplay((controller, components) -> {
                if (controller instanceof CoilWorkableElectricMultiblockMachine coilMachine && controller.isFormed()) {
                    components.add(Component.translatable("gtceu.multiblock.cracking_unit.energy",
                            Math.max(20, 100 - 10 * coilMachine.getCoilTier())));
                }
            })
            .register();

    public static final MultiblockMachineDefinition DISTILLATION_TOWER = REGISTRATE
            .multiblock("distillation_tower", DistillationTowerMachine::new)
            .nonYAxisRotation()
            .recipeType(GTRecipeTypes.DISTILLATION_RECIPES)
            .recipeModifiers(RecipeModifier.OVERCLOCKING)
            .appearanceBlock(CASING_STAINLESS_CLEAN)
            .structure(definition -> {
                TraceabilityPredicate exportPredicate = abilities(PartAbility.EXPORT_FLUIDS_1X);
                exportPredicate.setMaxLayerLimited(1, 1);
                TraceabilityPredicate maint = autoAbilities(true, false, false)
                        .setMaxGlobalLimited(1);
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .wherePart('Y', blocks(CASING_STAINLESS_CLEAN.get())
                                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(1))
                                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                        .setMaxGlobalLimited(2))
                                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setExactLimit(1))
                                .or(maint))
                        .where('Z', blocks(CASING_STAINLESS_CLEAN.get())
                                .or(exportPredicate)
                                .or(maint))
                        .where('X', blocks(CASING_STAINLESS_CLEAN.get()).or(exportPredicate))
                        .where('#', Predicates.air());
                var base = Piece.start(RIGHT, BACK, UP)
                        .aisle("YSY", "YYY", "YYY")
                        .aisle("ZZZ", "Z#Z", "ZZZ")
                        .portAfter(DistillationTowerMachine.LAYER_OUT)
                        .build();
                var layer = Piece.start(RIGHT, BACK, UP)
                        .aisle("XXX", "X#X", "XXX")
                        .portBefore(DistillationTowerMachine.LAYER_IN)
                        .portAfter(DistillationTowerMachine.LAYER_OUT)
                        .build();
                var top = Piece.start(RIGHT, BACK, UP)
                        .aisle("XXX", "XXX", "XXX")
                        .portBefore(DistillationTowerMachine.LAYER_IN)
                        .build();
                return Structure.root(base)
                        .symbols(symbols)
                        .atPort(DistillationTowerMachine.LAYER_OUT, Slot.chain(layer, DistillationTowerMachine.LAYER_IN,
                                DistillationTowerMachine.LAYER_OUT, DistillationTowerMachine.LAYER_IN)
                                .count(DistillationTowerMachine.LAYERS, 0, 10)
                                .atPort(DistillationTowerMachine.LAYER_OUT,
                                        Slot.one(top, DistillationTowerMachine.LAYER_IN)))
                        .build();
            })
            .allowExtendedFacing(false)
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_clean_stainless_steel"),
                    GTCEu.id("block/multiblock/distillation_tower"))
            .register();

    public static final MultiblockMachineDefinition VACUUM_FREEZER = REGISTRATE
            .multiblock("vacuum_freezer", WorkableElectricMultiblockMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.VACUUM_RECIPES)
            .recipeModifiers(RecipeModifier.OVERCLOCKING)
            .appearanceBlock(CASING_ALUMINIUM_FROSTPROOF)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .wherePart('X', blocks(CASING_ALUMINIUM_FROSTPROOF.get()).setMinGlobalLimited(14)
                                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                                .or(Predicates.autoAbilities(true, false, false)))
                        .where('#', Predicates.air());
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "X#X", "XXX")
                        .aisle("XXX", "XSX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_frost_proof"),
                    GTCEu.id("block/multiblock/vacuum_freezer"))
            .register();

    public static final MultiblockMachineDefinition ASSEMBLY_LINE = REGISTRATE
            .multiblock("assembly_line", AssemblyLineMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.ASSEMBLY_LINE_RECIPES)
            .recipeModifiers(RecipeModifier.OVERCLOCKING)
            .appearanceBlock(CASING_STEEL_SOLID)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .where('F', blocks(CASING_STEEL_SOLID.get())
                                .or(!ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids ?
                                        Predicates.abilities(PartAbility.IMPORT_FLUIDS_1X,
                                                PartAbility.IMPORT_FLUIDS_4X, PartAbility.IMPORT_FLUIDS_9X) :
                                        Predicates.abilities(PartAbility.IMPORT_FLUIDS_1X).setMaxGlobalLimited(4)))
                        .where('O',
                                Predicates.abilities(PartAbility.EXPORT_ITEMS)
                                        .addTooltips(Component.translatable("gtceu.multiblock.pattern.location_end")))
                        .wherePart('Y',
                                blocks(CASING_STEEL_SOLID.get())
                                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2)))
                        .where('I', blocks(ITEM_IMPORT_BUS[0].get()))
                        .where('G', blocks(CASING_GRATE.get()))
                        .where('A', blocks(CASING_ASSEMBLY_CONTROL.get()))
                        .where('R', blocks(CASING_LAMINATED_GLASS.get()))
                        .where('T', blocks(CASING_ASSEMBLY_LINE.get()))
                        .where('D', dataHatchPredicate(blocks(CASING_GRATE.get())))
                        .where('#', Predicates.any());
                var head = Piece.start(BACK, UP, RIGHT)
                        .aisle("FIF", "RTR", "SAG", "#Y#")
                        .portAfter(AssemblyLineMachine.SECTION_OUT)
                        .build();
                var section = Piece.start(BACK, UP, RIGHT)
                        .aisle("FIF", "RTR", "DAG", "#Y#")
                        .portBefore(AssemblyLineMachine.SECTION_IN)
                        .portAfter(AssemblyLineMachine.SECTION_OUT)
                        .build();
                var tail = Piece.start(BACK, UP, RIGHT)
                        .aisle("FOF", "RTR", "DAG", "#Y#")
                        .portBefore(AssemblyLineMachine.SECTION_IN)
                        .build();
                return Structure.root(head)
                        .symbols(symbols)
                        .atPort(AssemblyLineMachine.SECTION_OUT, Slot.chain(section, AssemblyLineMachine.SECTION_IN,
                                AssemblyLineMachine.SECTION_OUT, AssemblyLineMachine.SECTION_IN)
                                .count(AssemblyLineMachine.SECTIONS, 3, 15)
                                .atPort(AssemblyLineMachine.SECTION_OUT,
                                        Slot.one(tail, AssemblyLineMachine.SECTION_IN)))
                        .build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTCEu.id("block/multiblock/assembly_line"))
            .register();

    public static final MultiblockMachineDefinition PRIMITIVE_PUMP = REGISTRATE
            .multiblock("primitive_pump", PrimitivePumpMachine::new)
            .nonYAxisRotation()
            .appearanceBlock(CASING_PUMP_DECK)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .wherePart('X', blocks(CASING_PUMP_DECK.get()))
                        .where('F', Predicates.frames(GTMaterials.TreatedWood))
                        .where('H',
                                Predicates.abilities(PartAbility.PUMP_FLUID_HATCH)
                                        .or(blocks(FLUID_EXPORT_HATCH[ULV].get(), FLUID_EXPORT_HATCH[LV].get())))
                        .where('#', Predicates.any());
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXXX", "##F#", "##F#")
                        .aisle("XXHX", "F##F", "FFFF")
                        .aisle("SXXX", "##F#", "##F#")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .allowExtendedFacing(false)
            .sidedWorkableCasingRenderer("block/casings/pump_deck", GTCEu.id("block/multiblock/primitive_pump"))
            .register();

    public static final MultiblockMachineDefinition STEAM_GRINDER = REGISTRATE
            .multiblock("steam_grinder", SteamParallelMultiblockMachine::new)
            .allRotation()
            .appearanceBlock(CASING_BRONZE_BRICKS)
            .recipeType(GTRecipeTypes.MACERATOR_RECIPES)
            .recipeModifier(SteamParallelMultiblockMachine::recipeModifier)
            .addOutputLimit(ItemRecipeInfo.INSTANCE, 1)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .where('#', Predicates.air())
                        .wherePart('X', blocks(CASING_BRONZE_BRICKS.get()).setMinGlobalLimited(14)
                                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS).setPreviewCount(1))
                                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS).setPreviewCount(1))
                                .or(Predicates.abilities(PartAbility.STEAM).setExactLimit(1)));
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "X#X", "XXX")
                        .aisle("XXX", "XSX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                    GTCEu.id("block/multiblock/steam_grinder"))
            .register();

    public static final MultiblockMachineDefinition STEAM_OVEN = REGISTRATE
            .multiblock("steam_oven", SteamParallelMultiblockMachine::new)
            .allRotation()
            .appearanceBlock(CASING_BRONZE_BRICKS)
            .recipeType(GTRecipeTypes.FURNACE_RECIPES)
            .recipeModifier(SteamParallelMultiblockMachine::recipeModifier)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', Predicates.controller(definition))
                        .where('#', Predicates.air())
                        .where(' ', Predicates.any())
                        .wherePart('X', blocks(CASING_BRONZE_BRICKS.get()).setMinGlobalLimited(6)
                                .or(Predicates.abilities(PartAbility.STEAM_IMPORT_ITEMS).setPreviewCount(1))
                                .or(Predicates.abilities(PartAbility.STEAM_EXPORT_ITEMS).setPreviewCount(1)))
                        .where('F', blocks(FIREBOX_BRONZE.get())
                                .or(Predicates.abilities(PartAbility.STEAM).setExactLimit(1)));
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("FFF", "XXX", " X ")
                        .aisle("FFF", "X#X", " X ")
                        .aisle("FFF", "XSX", " X ")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .renderer(() -> new LargeBoilerRenderer(GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                    BoilerFireboxType.BRONZE_FIREBOX,
                    GTCEu.id("block/multiblock/steam_oven")))
            .register();

    public static final MultiblockMachineDefinition[] FUSION_REACTOR = registerTieredMultis("fusion_reactor",
            FusionReactorMachine::new, (tier, builder) -> builder
                    .allRotation()
                    .langValue("Fusion Reactor Computer MK %s".formatted(toRomanNumeral(tier - 5)))
                    .recipeType(GTRecipeTypes.FUSION_RECIPES)
                    .recipeModifiers(FusionReactorMachine::recipeModifier)
                    .tooltips(
                            Component.translatable("gtceu.machine.fusion_reactor.capacity",
                                    FusionReactorMachine.calculateEnergyStorageFactor(tier, 16) / 1000000L),
                            Component.translatable("gtceu.machine.fusion_reactor.overclocking"),
                            Component.translatable("gtceu.multiblock.%s_fusion_reactor.description"
                                    .formatted(VN[tier].toLowerCase(Locale.ROOT))))
                    .appearanceBlock(() -> FusionReactorMachine.getCasingState(tier))
                    .structure((definition) -> {
                        var casing = blocks(FusionReactorMachine.getCasingState(tier));
                        var symbols = Symbols.create()
                                .where('S', controller(definition))
                                .where('G', blocks(FUSION_GLASS.get()).or(casing))
                                .where('E', casing.or(
                                        blocks(PartAbility.INPUT_ENERGY.getBlockRange(tier, UV).toArray(Block[]::new))
                                                .setMinGlobalLimited(1).setPreviewCount(16)))
                                .where('C', casing)
                                .where('K', blocks(FusionReactorMachine.getCoilState(tier)))
                                .where('O', casing.or(abilities(PartAbility.EXPORT_FLUIDS)))
                                .where('A', air())
                                .wherePart('I', casing.or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(2)))
                                .where('#', any());
                        var piece = Piece.start(LEFT, UP, FRONT)
                                .aisle("###############", "######OGO######", "###############")
                                .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                                .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                                .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                                .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                                .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                                .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                                .aisle("#C###########C#", "GAG#########GAG", "#C###########C#")
                                .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                                .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                                .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                                .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                                .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                                .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                                .aisle("###############", "######OSO######", "###############")
                                .build();
                        return Structure.root(piece).symbols(symbols).build();
                    })
                    .renderer(() -> new FusionReactorRenderer(FusionReactorMachine.getCasingType(tier).getTexture(),
                            GTCEu.id("block/multiblock/fusion_reactor")))
                    .hasTESR(true)
                    .register(),
            LuV, ZPM, UV);

    public static final MultiblockMachineDefinition[] FLUID_DRILLING_RIG = registerTieredMultis(
            "fluid_drilling_rig", FluidDrillMachine::new, (tier, builder) -> builder
                    .nonYAxisRotation()
                    .langValue("%s Fluid Drilling Rig%s".formatted(VLVH[tier], VLVT[tier]))
                    .recipeType(DUMMY_RECIPES)
                    .tooltips(
                            Component.translatable("gtceu.machine.fluid_drilling_rig.description"),
                            Component.translatable("gtceu.machine.fluid_drilling_rig.depletion",
                                    FormattingUtil.formatNumbers(100.0 / FluidDrillMachine.getDepletionChance(tier))),
                            Component.translatable("gtceu.universal.tooltip.energy_tier_range", GTValues.VNF[tier],
                                    GTValues.VNF[tier + 1]),
                            Component.translatable("gtceu.machine.fluid_drilling_rig.production",
                                    FluidDrillMachine.getRigMultiplier(tier),
                                    FormattingUtil.formatNumbers(FluidDrillMachine.getRigMultiplier(tier) * 1.5)))
                    .appearanceBlock(() -> FluidDrillMachine.getCasingState(tier))
                    .structure((definition) -> {
                        var symbols = Symbols.create()
                                .where('S', controller(definition))
                                .wherePart('X', blocks(FluidDrillMachine.getCasingState(tier)).setMinGlobalLimited(3)
                                        .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                                .setMaxGlobalLimited(2))
                                        .or(abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1)))
                                .where('C', blocks(FluidDrillMachine.getCasingState(tier)))
                                .where('F', blocks(FluidDrillMachine.getFrameState(tier)))
                                .where('#', any());
                        var piece = Piece.start(LEFT, UP, FRONT)
                                .aisle("XXX", "#F#", "#F#", "#F#", "###", "###", "###")
                                .aisle("XXX", "FCF", "FCF", "FCF", "#F#", "#F#", "#F#")
                                .aisle("XSX", "#F#", "#F#", "#F#", "###", "###", "###")
                                .build();
                        return Structure.root(piece).symbols(symbols).build();
                    })
                    .workableCasingRenderer(FluidDrillMachine.getBaseTexture(tier),
                            GTCEu.id("block/multiblock/fluid_drilling_rig"))
                    .register(),
            MV, HV, EV);

    public static final MultiblockMachineDefinition[] LARGE_MINER = registerTieredMultis("large_miner",
            (holder, tier) -> new LargeMinerMachine(holder, tier, 64 / tier, 2 * tier - 5, tier, 8 - (tier - 5)),
            (tier, builder) -> builder
                    .nonYAxisRotation()
                    .langValue("%s Large Miner%s".formatted(VLVH[tier], VLVT[tier]))
                    .recipeType(GTRecipeTypes.MACERATOR_RECIPES)
                    .appearanceBlock(() -> LargeMinerMachine.getCasingState(tier))
                    .structure((definition) -> {
                        var symbols = Symbols.create()
                                .where('S', controller(definition))
                                .wherePart('X', blocks(LargeMinerMachine.getCasingState(tier))
                                        .or(abilities(PartAbility.EXPORT_ITEMS).setExactLimit(1).setPreviewCount(1))
                                        .or(abilities(PartAbility.IMPORT_FLUIDS).setExactLimit(1).setPreviewCount(1))
                                        .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                                .setMaxGlobalLimited(2).setPreviewCount(1)))
                                .where('C', blocks(LargeMinerMachine.getCasingState(tier)))
                                .where('F', frames(LargeMinerMachine.getMaterial(tier)))
                                .where('#', any());
                        var piece = Piece.start(LEFT, UP, FRONT)
                                .aisle("XXX", "#F#", "#F#", "#F#", "###", "###", "###")
                                .aisle("XXX", "FCF", "FCF", "FCF", "#F#", "#F#", "#F#")
                                .aisle("XSX", "#F#", "#F#", "#F#", "###", "###", "###")
                                .build();
                        return Structure.root(piece).symbols(symbols).build();
                    })
                    .allowExtendedFacing(true)
                    .renderer(() -> new LargeMinerRenderer(
                            MinerRenderer.MATERIALS_TO_CASING_MODELS.get(LargeMinerMachine.getMaterial(tier)),
                            GTCEu.id("block/multiblock/large_miner")))
                    .tooltips(
                            Component.translatable("gtceu.machine.large_miner.%s.tooltip"
                                    .formatted(VN[tier].toLowerCase(Locale.ROOT))),
                            Component.translatable("gtceu.machine.miner.multi.description"))
                    .tooltipBuilder((stack, tooltip) -> {
                        int workingAreaChunks = (2 * tier - 5);
                        tooltip.add(Component.translatable("gtceu.machine.miner.multi.modes"));
                        tooltip.add(Component.translatable("gtceu.machine.miner.multi.production"));
                        tooltip.add(Component.translatable("gtceu.machine.miner.fluid_usage", 8 - (tier - 5),
                                DrillingFluid.getLocalizedName()));
                        tooltip.add(Component.translatable("gtceu.universal.tooltip.working_area_chunks",
                                workingAreaChunks, workingAreaChunks));
                        tooltip.add(Component.translatable("gtceu.universal.tooltip.energy_tier_range",
                                GTValues.VNF[tier], GTValues.VNF[tier + 1]));
                    })
                    .register(),
            EV, IV, LuV);

    public static final MultiblockMachineDefinition CLEANROOM = REGISTRATE
            .multiblock("cleanroom", CleanroomMachine::new)
            .noneRotation()
            .recipeType(DUMMY_RECIPES)
            .appearanceBlock(PLASTCRETE)
            .tooltips(Component.translatable("gtceu.machine.cleanroom.tooltip.0"),
                    Component.translatable("gtceu.machine.cleanroom.tooltip.1"),
                    Component.translatable("gtceu.machine.cleanroom.tooltip.2"),
                    Component.translatable("gtceu.machine.cleanroom.tooltip.3"))
            .tooltipBuilder((stack, tooltip) -> {
                if (GTUtil.isCtrlDown()) {
                    tooltip.add(Component.empty());
                    tooltip.add(Component.translatable("gtceu.machine.cleanroom.tooltip.4"));
                    tooltip.add(Component.translatable("gtceu.machine.cleanroom.tooltip.5"));
                    tooltip.add(Component.translatable("gtceu.machine.cleanroom.tooltip.6"));
                    tooltip.add(Component.translatable("gtceu.machine.cleanroom.tooltip.7"));
                    // tooltip.add(Component.translatable("gtceu.machine.cleanroom.tooltip.8"));
                    tooltip.add(Component.translatable(AEConfig.instance().getChannelMode() == ChannelMode.INFINITE ? "gtceu.machine.cleanroom.tooltip.ae2.no_channels" : "gtceu.machine.cleanroom.tooltip.ae2.channels"));
                    tooltip.add(Component.empty());
                } else {
                    tooltip.add(Component.translatable("gtceu.machine.cleanroom.tooltip.hold_ctrl"));
                }
            })
            .structure(CleanroomMachine::structure)
            .workableCasingRenderer(GTCEu.id("block/casings/cleanroom/plascrete"),
                    GTCEu.id("block/multiblock/cleanroom"))
            .register();

    public static final MultiblockMachineDefinition LARGE_COMBUSTION_ENGINE = registerLargeCombustionEngine(
            "large_combustion_engine", EV,
            CASING_TITANIUM_STABLE, CASING_TITANIUM_GEARBOX, CASING_ENGINE_INTAKE,
            GTCEu.id("block/casings/solid/machine_casing_stable_titanium"),
            GTCEu.id("block/multiblock/generator/large_combustion_engine"));

    public static final MultiblockMachineDefinition EXTREME_COMBUSTION_ENGINE = registerLargeCombustionEngine(
            "extreme_combustion_engine", IV,
            CASING_TUNGSTENSTEEL_ROBUST, CASING_TUNGSTENSTEEL_GEARBOX, CASING_EXTREME_ENGINE_INTAKE,
            GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
            GTCEu.id("block/multiblock/generator/extreme_combustion_engine"));

    public static final MultiblockMachineDefinition LARGE_STEAM_TURBINE = registerLargeTurbine("steam_large_turbine",
            HV,
            GTRecipeTypes.STEAM_TURBINE_FUELS,
            CASING_STEEL_TURBINE, CASING_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_steel"),
            GTCEu.id("block/multiblock/generator/large_steam_turbine"),
            false);

    public static final MultiblockMachineDefinition LARGE_GAS_TURBINE = registerLargeTurbine("gas_large_turbine", EV,
            GTRecipeTypes.GAS_TURBINE_FUELS,
            CASING_STAINLESS_TURBINE, CASING_STAINLESS_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_stainless_steel"),
            GTCEu.id("block/multiblock/generator/large_gas_turbine"),
            true);

    public static final MultiblockMachineDefinition LARGE_PLASMA_TURBINE = registerLargeTurbine("plasma_large_turbine",
            IV,
            GTRecipeTypes.PLASMA_GENERATOR_FUELS,
            CASING_TUNGSTENSTEEL_TURBINE, CASING_TUNGSTENSTEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_tungstensteel"),
            GTCEu.id("block/multiblock/generator/large_plasma_turbine"),
            false);

    public static final MultiblockMachineDefinition ACTIVE_TRANSFORMER = REGISTRATE
            .multiblock("active_transformer", ActiveTransformerMachine::new)
            .allRotation()
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .appearanceBlock(HIGH_POWER_CASING)
            .tooltips(Component.translatable("gtceu.machine.active_transformer.tooltip.0"),
                    Component.translatable("gtceu.machine.active_transformer.tooltip.1"))
            .tooltipBuilder(
                    (stack,
                     components) -> components.add(Component.translatable("gtceu.machine.active_transformer.tooltip.2")
                             .append(Component.translatable("gtceu.machine.active_transformer.tooltip.3")
                                     .withStyle(TooltipHelper.RAINBOW_HSL_SLOW))))
            .structure((definition) -> {
                var symbols = Symbols.create()
                        .where('S', controller(definition))
                        .wherePart('X', blocks(GTBlocks.HIGH_POWER_CASING.get()).setMinGlobalLimited(12)
                                .or(ActiveTransformerMachine.getHatchPredicates()))
                        .where('C', blocks(GTBlocks.SUPERCONDUCTING_COIL.get()));
                var piece = Piece.start(LEFT, UP, FRONT)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "XCX", "XXX")
                        .aisle("XXX", "XSX", "XXX")
                        .build();
                return Structure.root(piece).symbols(symbols).build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/hpca/high_power_casing"),
                    GTCEu.id("block/multiblock/data_bank"))
            .register();

    public static final MultiblockMachineDefinition POWER_SUBSTATION = REGISTRATE
            .multiblock("power_substation", PowerSubstationMachine::new)
            .checkPriority(3)
            .nonYAxisRotation()
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.power_substation.tooltip.0"),
                    Component.translatable("gtceu.machine.power_substation.tooltip.1"),
                    Component.translatable("gtceu.machine.power_substation.tooltip.2",
                            PowerSubstationMachine.MAX_BATTERY_LAYERS),
                    Component.translatable("gtceu.machine.power_substation.tooltip.3"),
                    Component.translatable("gtceu.machine.power_substation.tooltip.4",
                            PowerSubstationMachine.PASSIVE_DRAIN_MAX_PER_STORAGE / 1000))
            .tooltipBuilder(
                    (stack,
                     components) -> components.add(Component.translatable("gtceu.machine.power_substation.tooltip.5")
                             .append(Component.translatable("gtceu.machine.power_substation.tooltip.6")
                                     .withStyle(TooltipHelper.RAINBOW_HSL_SLOW))))
            .appearanceBlock(CASING_PALLADIUM_SUBSTATION)
            .structure(definition -> {
                var symbols = Symbols.create()
                        .where('S', controller(definition))
                        .where('C', blocks(CASING_PALLADIUM_SUBSTATION.get()))
                        .wherePart('X',
                                blocks(CASING_PALLADIUM_SUBSTATION.get())
                                        .setMinGlobalLimited(PowerSubstationMachine.MIN_CASINGS)
                                        .or(autoAbilities(true, false, false)))
                        .where('G', blocks(CASING_LAMINATED_GLASS.get()))
                        .where('B', Predicates.powerSubstationBatteries());
                var base = Piece.start(RIGHT, BACK, UP)
                        .aisle("XXSXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX")
                        .aisle("XXXXX", "XCCCX", "XCCCX", "XCCCX", "XXXXX")
                        .portAfter(PowerSubstationMachine.LAYER_OUT)
                        .build();
                var layer = Piece.start(RIGHT, BACK, UP)
                        .aisle("GGGGG", "GBBBG", "GBBBG", "GBBBG", "GGGGG")
                        .portBefore(PowerSubstationMachine.LAYER_IN)
                        .portAfter(PowerSubstationMachine.LAYER_OUT)
                        .build();
                var top = Piece.start(RIGHT, BACK, UP)
                        .aisle("GGGGG", "GGGGG", "GGGGG", "GGGGG", "GGGGG")
                        .portBefore(PowerSubstationMachine.LAYER_IN)
                        .build();
                return Structure.root(base)
                        .symbols(symbols)
                        .atPort(PowerSubstationMachine.LAYER_OUT, Slot.chain(layer, PowerSubstationMachine.LAYER_IN,
                                PowerSubstationMachine.LAYER_OUT, PowerSubstationMachine.LAYER_IN)
                                .count(PowerSubstationMachine.LAYERS, 1, PowerSubstationMachine.MAX_BATTERY_LAYERS)
                                .atPort(PowerSubstationMachine.LAYER_OUT,
                                        Slot.one(top, PowerSubstationMachine.LAYER_IN)))
                        .build();
            })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_palladium_substation"),
                    GTCEu.id("block/multiblock/power_substation"))
            .register();

    public static final MultiblockMachineDefinition CHARCOAL_PILE_IGNITER = REGISTRATE
            .multiblock("charcoal_pile_igniter", CharcoalPileIgniterMachine::new)
            .noneRotation()
            .recipeType(DUMMY_RECIPES)
            .appearanceBlock(BRONZE_HULL)
            .structure(CharcoalPileIgniterMachine::structure)
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                    GTCEu.id("block/multiblock/charcoal_pile_igniter"))
            .register();

    public static MultiblockMachineDefinition[] BEDROCK_ORE_MINER = registerTieredMultis(
            "bedrock_ore_miner", BedrockOreMinerMachine::new, (tier, builder) -> builder
                    .nonYAxisRotation()
                    .langValue("%s Bedrock Ore Miner%s".formatted(VLVH[tier], VLVT[tier]))
                    .recipeType(DUMMY_RECIPES)
                    .tooltips(
                            Component.translatable("gtceu.machine.bedrock_ore_miner.description"),
                            Component.translatable("gtceu.machine.bedrock_ore_miner.depletion",
                                    FormattingUtil.formatNumbers(
                                            100.0 / BedrockOreMinerMachine.getDepletionChance(tier))),
                            Component.translatable("gtceu.universal.tooltip.energy_tier_range",
                                    GTValues.VNF[tier], GTValues.VNF[tier + 1]),
                            Component.translatable("gtceu.machine.bedrock_ore_miner.production",
                                    BedrockOreMinerMachine.getRigMultiplier(tier),
                                    FormattingUtil.formatNumbers(
                                            BedrockOreMinerMachine.getRigMultiplier(tier) * 1.5)))
                    .appearanceBlock(() -> BedrockOreMinerMachine.getCasingState(tier))
                    .structure((definition) -> {
                        var symbols = Symbols.create()
                                .where('S', controller(definition))
                                .wherePart('X',
                                        blocks(BedrockOreMinerMachine.getCasingState(tier)).setMinGlobalLimited(3)
                                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                                        .setMaxGlobalLimited(2))
                                                .or(abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(1)))
                                .where('C', blocks(BedrockOreMinerMachine.getCasingState(tier)))
                                .where('F', blocks(BedrockOreMinerMachine.getFrameState(tier)))
                                .where('#', any());
                        var piece = Piece.start(LEFT, UP, FRONT)
                                .aisle("XXX", "#F#", "#F#", "#F#", "###", "###", "###")
                                .aisle("XXX", "FCF", "FCF", "FCF", "#F#", "#F#", "#F#")
                                .aisle("XSX", "#F#", "#F#", "#F#", "###", "###", "###")
                                .build();
                        return Structure.root(piece).symbols(symbols).build();
                    })
                    .workableCasingRenderer(BedrockOreMinerMachine.getBaseTexture(tier),
                            GTCEu.id("block/multiblock/bedrock_ore_miner"))
                    .register(),
            MV, HV, EV);

    // Multiblock Tanks
    public static final MachineDefinition WOODEN_TANK_VALVE = GTMachineUtils.registerTankValve(
            "wooden_tank_valve", "Wooden Tank Valve", false,
            (builder, overlay) -> builder.sidedWorkableCasingRenderer("block/casings/wood_wall", overlay));
    public static final MultiblockMachineDefinition WOODEN_MULTIBLOCK_TANK = registerMultiblockTank(
            "wooden_multiblock_tank", "Wooden Multiblock Tank", 100 * 10000,
            CASING_WOOD_WALL, WOODEN_TANK_VALVE::get,
            new PropertyFluidFilter(false, false),
            (builder, overlay) -> builder.sidedWorkableCasingRenderer("block/casings/wood_wall", overlay));

    public static final MachineDefinition BRONZE_TANK_VALVE = GTMachineUtils.registerTankValve(
            "bronze_tank_valve", "Bronze Tank Valve", true,
            (builder, overlay) -> builder
                    .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"), overlay));
    public static final MultiblockMachineDefinition BRONZE_MULTIBLOCK_TANK = registerMultiblockTank(
            "bronze_multiblock_tank", "Bronze Multiblock Tank", 250 * 10000,
            CASING_BRONZE_BRICKS, BRONZE_TANK_VALVE::get,
            new PropertyFluidFilter(true, false),
            (builder, overlay) -> builder
                    .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"), overlay));

    public static final MachineDefinition STEEL_TANK_VALVE = GTMachineUtils.registerTankValve(
            "steel_tank_valve", "Steel Tank Valve", true,
            (builder, overlay) -> builder.workableCasingRenderer(
                    GTCEu.id("block/casings/solid/machine_casing_solid_steel"), overlay));
    public static final MultiblockMachineDefinition STEEL_MULTIBLOCK_TANK = registerMultiblockTank(
            "steel_multiblock_tank", "Steel Multiblock Tank", 1000 * 10000,
            CASING_STEEL_SOLID, STEEL_TANK_VALVE::get,
            null,
            (builder, overlay) -> builder.workableCasingRenderer(
                    GTCEu.id("block/casings/solid/machine_casing_solid_steel"), overlay));

    public static void init() {}
}
