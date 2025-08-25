package com.gregtechceu.gtceu.data.recipe.generated;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.NO_SMASHING;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Iron;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.*;

public final class PipeRecipeHandler {

    private PipeRecipeHandler() {}

    public static void run(@NotNull Material material) {
        processPipeTiny(PropertyKey.FLUID_PIPE, pipeTinyFluid, material);
        processPipeSmall(PropertyKey.FLUID_PIPE, pipeSmallFluid, material);
        processPipeNormal(PropertyKey.FLUID_PIPE, pipeNormalFluid, material);
        processPipeLarge(PropertyKey.FLUID_PIPE, pipeLargeFluid, material);
        processPipeHuge(PropertyKey.FLUID_PIPE, pipeHugeFluid, material);
        processPipeQuadruple(PropertyKey.FLUID_PIPE, pipeQuadrupleFluid, material);
        processPipeNonuple(PropertyKey.FLUID_PIPE, pipeNonupleFluid, material);

        processPipeSmall(PropertyKey.ITEM_PIPE, pipeSmallItem, material);
        processPipeNormal(PropertyKey.ITEM_PIPE, pipeNormalItem, material);
        processPipeLarge(PropertyKey.ITEM_PIPE, pipeLargeItem, material);
        processPipeHuge(PropertyKey.ITEM_PIPE, pipeHugeItem, material);
        processRestrictivePipe(PropertyKey.ITEM_PIPE, pipeSmallRestrictive, pipeSmallItem, material);
        processRestrictivePipe(PropertyKey.ITEM_PIPE, pipeNormalRestrictive, pipeNormalItem, material);
        processRestrictivePipe(PropertyKey.ITEM_PIPE, pipeLargeRestrictive, pipeLargeItem, material);
        processRestrictivePipe(PropertyKey.ITEM_PIPE, pipeHugeRestrictive, pipeHugeItem, material);
    }

    private static void processRestrictivePipe(@NotNull PropertyKey<?> propertyKey,
                                               @NotNull TagPrefix prefix, @NotNull TagPrefix unrestrictive,
                                               @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        ASSEMBLER_RECIPES.recipeBuilder("assemble_" + material.getName() + "_" + prefix.name.toLowerCase(Locale.ROOT))
                .inputItems(unrestrictive, material)
                .inputItems(ring, Iron, 2)
                .outputItems(prefix, material)
                .duration(20)
                .EUt(VA[ULV])
                .save();

        VanillaRecipeHelper.addShapedRecipe(
                FormattingUtil.toLowerCaseUnderscore(prefix + "_" + material.getName()),
                ChemicalHelper.get(prefix, material), "PR", "Rh",
                'P', new MaterialEntry(unrestrictive, material), 'R', ChemicalHelper.get(ring, Iron));
    }

    private static void processPipeTiny(@NotNull PropertyKey<?> propertyKey,
                                        @NotNull TagPrefix prefix, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        if (material.hasProperty(PropertyKey.WOOD)) return;
        ItemStack pipeStack = ChemicalHelper.get(prefix, material);
        EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_tiny_pipe")
                .inputItems(ingot, material, 1)
                .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_TINY)
                .outputItems(pipeStack.copyWithCount(2))
                .duration((int) (material.getMass()))
                .EUt(6L * getVoltageMultiplier(material))
                .save();

        if (material.hasFlag(NO_SMASHING)) {
            EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_tiny_pipe_dust")
                    .inputItems(dust, material, 1)
                    .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_TINY)
                    .outputItems(pipeStack.copyWithCount(2))
                    .duration((int) (material.getMass()))
                    .EUt(6L * getVoltageMultiplier(material))
                    .save();
        } else {
            VanillaRecipeHelper.addShapedRecipe(String.format("tiny_%s_pipe", material.getName()),
                    pipeStack.copyWithCount(2), " s ", "hXw",
                    'X', new MaterialEntry(plate, material));
        }
    }

    private static void processPipeSmall(@NotNull PropertyKey<?> propertyKey,
                                         @NotNull TagPrefix prefix, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        if (material.hasProperty(PropertyKey.WOOD)) return;
        ItemStack pipeStack = ChemicalHelper.get(prefix, material);
        EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_small_pipe")
                .inputItems(ingot, material, 1)
                .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_SMALL)
                .outputItems(pipeStack)
                .duration((int) (material.getMass()))
                .EUt(6L * getVoltageMultiplier(material))
                .save();

        if (material.hasFlag(NO_SMASHING)) {
            EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_small_pipe_dust")
                    .inputItems(dust, material, 1)
                    .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_SMALL)
                    .outputItems(pipeStack)
                    .duration((int) (material.getMass()))
                    .EUt(6L * getVoltageMultiplier(material))
                    .save();
        } else {
            VanillaRecipeHelper.addShapedRecipe(String.format("small_%s_pipe", material.getName()),
                    pipeStack, "wXh",
                    'X', new MaterialEntry(plate, material));
        }
    }

    private static void processPipeNormal(@NotNull PropertyKey<?> propertyKey,
                                          @NotNull TagPrefix prefix, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        if (material.hasProperty(PropertyKey.WOOD)) return;
        ItemStack pipeStack = ChemicalHelper.get(prefix, material);
        EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_pipe")
                .inputItems(ingot, material, 3)
                .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_NORMAL)
                .outputItems(pipeStack)
                .duration((int) material.getMass() * 3)
                .EUt(6L * getVoltageMultiplier(material))
                .save();

        if (material.hasFlag(NO_SMASHING)) {
            EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_pipe_dust")
                    .inputItems(dust, material, 3)
                    .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_NORMAL)
                    .outputItems(pipeStack)
                    .duration((int) material.getMass() * 3)
                    .EUt(6L * getVoltageMultiplier(material))
                    .save();
        } else {
            VanillaRecipeHelper.addShapedRecipe(String.format("medium_%s_pipe", material.getName()),
                    pipeStack, "XXX", "w h",
                    'X', new MaterialEntry(plate, material));
        }
    }

    private static void processPipeLarge(@NotNull PropertyKey<?> propertyKey,
                                         @NotNull TagPrefix prefix, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        if (material.hasProperty(PropertyKey.WOOD)) return;
        ItemStack pipeStack = ChemicalHelper.get(prefix, material);
        EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_large_pipe")
                .inputItems(ingot, material, 6)
                .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_LARGE)
                .outputItems(pipeStack)
                .duration((int) material.getMass() * 6)
                .EUt(6L * getVoltageMultiplier(material))
                .save();

        if (material.hasFlag(NO_SMASHING)) {
            EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_large_pipe_dust")
                    .inputItems(dust, material, 6)
                    .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_LARGE)
                    .outputItems(pipeStack)
                    .duration((int) material.getMass() * 6)
                    .EUt(6L * getVoltageMultiplier(material))
                    .save();
        } else {
            VanillaRecipeHelper.addShapedRecipe(String.format("large_%s_pipe", material.getName()),
                    pipeStack, "XXX", "w h", "XXX",
                    'X', new MaterialEntry(plate, material));
        }
    }

    private static void processPipeHuge(@NotNull PropertyKey<?> propertyKey,
                                        @NotNull TagPrefix prefix, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        if (material.hasProperty(PropertyKey.WOOD)) return;
        ItemStack pipeStack = ChemicalHelper.get(prefix, material);
        EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_huge_pipe")
                .inputItems(ingot, material, 12)
                .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_HUGE)
                .outputItems(pipeStack)
                .duration((int) material.getMass() * 24)
                .EUt(6L * getVoltageMultiplier(material))
                .save();

        if (material.hasFlag(NO_SMASHING)) {
            EXTRUDER_RECIPES.recipeBuilder("extrude_" + material.getName() + "_huge_pipe_dust")
                    .inputItems(dust, material, 12)
                    .notConsumable(GTItems.SHAPE_EXTRUDER_PIPE_HUGE)
                    .outputItems(pipeStack)
                    .duration((int) material.getMass() * 24)
                    .EUt(6L * getVoltageMultiplier(material))
                    .save();
        } else if (plateDouble.doGenerateItem(material)) {
            VanillaRecipeHelper.addShapedRecipe(String.format("huge_%s_pipe", material.getName()),
                    pipeStack, "XXX", "w h", "XXX",
                    'X', new MaterialEntry(plateDouble, material));
        }
    }

    private static void processPipeQuadruple(@NotNull PropertyKey<?> propertyKey,
                                             @NotNull TagPrefix prefix, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        if (material.hasProperty(PropertyKey.WOOD)) return;
        ItemStack smallPipe = ChemicalHelper.get(pipeSmallFluid, material);
        ItemStack quadPipe = ChemicalHelper.get(prefix, material);
        VanillaRecipeHelper.addShapedRecipe(String.format("quadruple_%s_pipe", material.getName()),
                quadPipe, "XX", "XX",
                'X', smallPipe);

        PACKER_RECIPES.recipeBuilder("package_" + material.getName() + "_quadruple_pipe")
                .inputItems(smallPipe.copyWithCount(4))
                .circuitMeta(4)
                .outputItems(quadPipe)
                .duration(30)
                .EUt(VA[ULV])
                .save();
    }

    private static void processPipeNonuple(@NotNull PropertyKey<?> propertyKey,
                                           @NotNull TagPrefix prefix, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(propertyKey)) {
            return;
        }

        if (material.hasProperty(PropertyKey.WOOD)) return;
        ItemStack smallPipe = ChemicalHelper.get(pipeSmallFluid, material);
        ItemStack nonuplePipe = ChemicalHelper.get(prefix, material);
        VanillaRecipeHelper.addShapedRecipe(String.format("nonuple_%s_pipe", material.getName()),
                nonuplePipe, "XXX", "XXX", "XXX",
                'X', smallPipe);

        PACKER_RECIPES.recipeBuilder("package_" + material.getName() + "_nonuple_pipe")
                .inputItems(smallPipe.copyWithCount(9))
                .circuitMeta(9)
                .outputItems(nonuplePipe)
                .duration(40)
                .EUt(VA[ULV])
                .save();
    }

    private static int getVoltageMultiplier(Material material) {
        return material.getBlastTemperature() >= 2800 ? VA[LV] : VA[ULV];
    }
}
