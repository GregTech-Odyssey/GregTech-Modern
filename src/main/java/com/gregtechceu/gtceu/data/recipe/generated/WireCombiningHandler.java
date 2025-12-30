package com.gregtechceu.gtceu.data.recipe.generated;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.PACKER_RECIPES;

public final class WireCombiningHandler {

    private static final TagPrefix[] WIRE_DOUBLING_ORDER = new TagPrefix[] {
            wireGtSingle, wireGtDouble, wireGtQuadruple, wireGtOctal, wireGtHex
    };

    private static final Map<TagPrefix, TagPrefix> cableToWireMap = Map.of(
            cableGtSingle, wireGtSingle,
            cableGtDouble, wireGtDouble,
            cableGtQuadruple, wireGtQuadruple,
            cableGtOctal, wireGtOctal,
            cableGtHex, wireGtHex);

    private WireCombiningHandler() {}

    public static void run(@NotNull Material material) {
        // Generate Wire Packer/Unpacker recipes
        processWireCompression(material);

        // Generate manual recipes for combining Wires/Cables
        for (int i = 0; i < WIRE_DOUBLING_ORDER.length; i++) {
            generateWireCombiningRecipe(i, material);
        }

        // Generate Cable -> Wire recipes in the unpacker
        for (TagPrefix prefix : cableToWireMap.keySet()) {
            processCableStripping(prefix, material);
        }
    }

    private static void generateWireCombiningRecipe(int index,
                                                    @NotNull Material material) {
        TagPrefix prefix = WIRE_DOUBLING_ORDER[index];
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(PropertyKey.WIRE)) {
            return;
        }
        var wireStack = new MaterialEntry(prefix, material);
        if (index < WIRE_DOUBLING_ORDER.length - 1) {
            VanillaRecipeHelper.addShapelessRecipe(
                    String.format("%s_wire_%s_doubling", material.getName(), prefix.name.toLowerCase(Locale.ROOT)),
                    ChemicalHelper.get(WIRE_DOUBLING_ORDER[index + 1], material),
                    wireStack, wireStack);
        }

        if (index > 0) {
            VanillaRecipeHelper.addShapelessRecipe(
                    String.format("%s_wire_%s_splitting", material.getName(), prefix.name.toLowerCase(Locale.ROOT)),
                    ChemicalHelper.get(WIRE_DOUBLING_ORDER[index - 1], material, 2),
                    wireStack);
        }

        if (index < WIRE_DOUBLING_ORDER.length - 2) {
            VanillaRecipeHelper.addShapelessRecipe(
                    String.format("%s_wire_%s_quadrupling", material.getName(), prefix.name.toLowerCase(Locale.ROOT)),
                    ChemicalHelper.get(WIRE_DOUBLING_ORDER[index + 2], material),
                    wireStack, wireStack, wireStack, wireStack);
        }
    }

    private static void processWireCompression(@NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(wireGtSingle) || !material.hasProperty(PropertyKey.WIRE)) {
            return;
        }

        for (int startTier = 0; startTier < 4; startTier++) {
            for (int i = 1; i < 5 - startTier; i++) {
                PACKER_RECIPES.recipeBuilder("pack_" + material.getName() + "_wires_" + i + "_" + startTier)
                        .inputItems(WIRE_DOUBLING_ORDER[startTier], material, 1 << i)
                        .circuitMeta((int) Math.pow(2, i))
                        .outputItems(WIRE_DOUBLING_ORDER[startTier + i], material, 1)
                        .save();
            }
        }

        for (int i = 1; i < 5; i++) {
            PACKER_RECIPES.recipeBuilder("pack_" + material.getName() + "_wires_" + i + "_single")
                    .inputItems(WIRE_DOUBLING_ORDER[i], material, 1)
                    .circuitMeta(1)
                    .outputItems(WIRE_DOUBLING_ORDER[0], material, (int) Math.pow(2, i))
                    .save();
        }
    }

    private static void processCableStripping(@NotNull TagPrefix prefix,
                                              @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(prefix) || !material.hasProperty(PropertyKey.WIRE)) {
            return;
        }

        PACKER_RECIPES.recipeBuilder("strip_" + material.getName() + "_" + prefix.name.toLowerCase(Locale.ROOT))
                .inputItems(prefix, material)
                .outputItems(cableToWireMap.get(prefix), material)
                .outputItems(plate, GTMaterials.Rubber,
                        (int) (prefix.secondaryMaterials().getFirst().amount() / GTValues.M))
                .duration(100).EUt(GTValues.VA[GTValues.ULV])
                .save();
    }
}
