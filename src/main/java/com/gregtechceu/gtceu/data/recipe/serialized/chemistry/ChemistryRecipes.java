package com.gregtechceu.gtceu.data.recipe.serialized.chemistry;

import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dust;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.ingot;
import static com.gregtechceu.gtceu.common.data.GTMaterials.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.*;

public class ChemistryRecipes {

    public static void init() {
        AcidRecipes.init();
        BrewingRecipes.init();
        ChemicalBathRecipes.init();
        DistillationRecipes.init();
        FuelRecipeChains.init();
        GemSlurryRecipes.init();
        GrowthMediumRecipes.init();
        LCRCombined.init();
        MixerRecipes.init();
        NaquadahRecipes.init();
        NuclearRecipes.init();
        PetrochemRecipes.init();
        PlatGroupMetalsRecipes.init();
        PolymerRecipes.init();
        ReactorRecipes.init();
        SeparationRecipes.init();
        AntidoteRecipes.init();
        TitaniumRecipes.init();

        // A Few Random Recipes
        FLUID_HEATER_RECIPES.recipeBuilder("ethenone")
                .circuitMeta(1)
                .inputFluids(Acetone.getFluid(100))
                .outputFluids(Ethenone.getFluid(100))
                .duration(16).EUt(VA[LV]).save();

        FLUID_HEATER_RECIPES.recipeBuilder("acetone")
                .circuitMeta(1)
                .inputFluids(DissolvedCalciumAcetate.getFluid(200))
                .outputFluids(Acetone.getFluid(200))
                .duration(16).EUt(VA[LV]).save();

        VACUUM_RECIPES.recipeBuilder("ice")
                .inputFluids(Water.getFluid(1000))
                .outputFluids(Ice.getFluid(1000))
                .duration(50).EUt(VA[LV]).save();

        VACUUM_RECIPES.recipeBuilder("liquid_air")
                .inputFluids(Air.getFluid(4000))
                .outputFluids(LiquidAir.getFluid(4000))
                .duration(80).EUt(VA[HV]).save();

        VACUUM_RECIPES.recipeBuilder("liquid_nether_air")
                .inputFluids(NetherAir.getFluid(4000))
                .outputFluids(LiquidNetherAir.getFluid(4000))
                .duration(80).EUt(VA[EV]).save();

        VACUUM_RECIPES.recipeBuilder("liquid_ender_air")
                .inputFluids(EnderAir.getFluid(4000))
                .outputFluids(LiquidEnderAir.getFluid(4000))
                .duration(80).EUt(VA[IV]).save();

        VACUUM_RECIPES.recipeBuilder("liquid_oxygen")
                .inputFluids(Oxygen.getFluid(1000))
                .outputFluids(Oxygen.getFluid(FluidStorageKeys.LIQUID, 1000))
                .duration(240).EUt(VA[EV]).save();

        VACUUM_RECIPES.recipeBuilder("liquid_helium")
                .inputFluids(Helium.getFluid(1000))
                .outputFluids(Helium.getFluid(FluidStorageKeys.LIQUID, 1000))
                .duration(240).EUt(VA[EV]).save();

        BLAST_RECIPES.recipeBuilder("nickel_zinc_ferrite")
                .inputItems(dust, FerriteMixture)
                .inputFluids(Oxygen.getFluid(2000))
                .outputItems(ingot, NickelZincFerrite)
                .blastFurnaceTemp(1500)
                .duration(400).EUt(VA[MV]).save();

        FERMENTING_RECIPES.recipeBuilder("fermented_biomass")
                .inputFluids(Biomass.getFluid(100))
                .outputFluids(FermentedBiomass.getFluid(100))
                .duration(150).EUt(2).save();

        WIREMILL_RECIPES.recipeBuilder("string_from_polycaprolactam")
                .inputItems(ingot, Polycaprolactam)
                .outputItems(Items.STRING, 32)
                .duration(80).EUt(48).save();

        GAS_COLLECTOR_RECIPES.recipeBuilder("air")
                .circuitMeta(1)
                .outputFluids(Air.getFluid(10000))
                .dimension(new ResourceLocation("overworld"))
                .duration(200).EUt(16).save();

        GAS_COLLECTOR_RECIPES.recipeBuilder("nether_air")
                .circuitMeta(2)
                .outputFluids(NetherAir.getFluid(10000))
                .dimension(new ResourceLocation("the_nether"))
                .duration(200).EUt(64).save();

        GAS_COLLECTOR_RECIPES.recipeBuilder("ender_air")
                .circuitMeta(3)
                .outputFluids(EnderAir.getFluid(10000))
                .dimension(new ResourceLocation("the_end"))
                .duration(200).EUt(256).save();

        // CaCO3 + 2NaCl -> Na2CO3 + CaCl2
        BLAST_RECIPES.recipeBuilder("soda_ash_from_calcite")
                .inputItems(dust, Calcite, 5)
                .inputItems(dust, Salt, 4)
                .outputItems(dust, SodaAsh, 6)
                .outputItems(dust, CalciumChloride, 3)
                .duration(120).EUt(VA[MV]).blastFurnaceTemp(1500)
                .save();

        // 2NaOH + CO2 -> Na2CO3 + H20
        CHEMICAL_RECIPES.recipeBuilder("soda_ash_from_carbon_dioxide")
                .inputItems(dust, SodiumHydroxide, 6)
                .inputFluids(CarbonDioxide.getFluid(1000))
                .outputItems(dust, SodaAsh, 6)
                .outputFluids(Water.getFluid(1000))
                .duration(80).EUt(VA[HV])
                .save();

        PYROLYSE_RECIPES.recipeBuilder("activated_carbon_from_carbon").circuitMeta(1)
                .inputItems(dust, Carbon)
                .inputFluids(Nitrogen.getFluid(2000))
                .outputItems(dust, ActivatedCarbon)
                .duration(320).EUt(64)
                .save();

        PYROLYSE_RECIPES.recipeBuilder("activated_carbon_from_charcoal").circuitMeta(1)
                .inputItems(dust, Charcoal)
                .inputFluids(Nitrogen.getFluid(2000))
                .outputItems(dust, ActivatedCarbon)
                .duration(640).EUt(64)
                .save();

        // 4 kelp -> 1 iodine
        CHEMICAL_RECIPES.recipeBuilder("iodine")
                .inputItems(Items.DRIED_KELP_BLOCK, 3)
                .inputFluids(Hydrogen.getFluid(2000))
                .inputFluids(HydrogenPeroxide.getFluid(1000))
                .outputItems(dust, Iodine, 1)
                .outputFluids(Water.getFluid(2000))
                .duration(200).EUt(VA[MV])
                .save();
    }
}
