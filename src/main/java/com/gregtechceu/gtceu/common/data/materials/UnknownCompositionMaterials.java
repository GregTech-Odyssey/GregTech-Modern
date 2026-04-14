package com.gregtechceu.gtceu.common.data.materials;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.HazardProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ToolProperty;
import com.gregtechceu.gtceu.api.fluids.FluidBuilder;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTMedicalConditions;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.*;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.*;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dustSmall;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dustTiny;
import static com.gregtechceu.gtceu.common.data.GTMaterials.*;

public class UnknownCompositionMaterials {

    public static void register() {
        WoodGas = GTMaterials.builder("wood_gas")
                .gas()
                .color(0xDECD87).secondaryColor(0xdeb287)
                .buildAndRegister();

        WoodVinegar = GTMaterials.builder("wood_vinegar")
                .fluid()
                .color(0xD45500).secondaryColor(0x905800)
                .buildAndRegister();

        WoodTar = GTMaterials.builder("wood_tar")
                .fluid()
                .color(0x3a271a).secondaryColor(0x28170B)
                .flags(STICKY, FLAMMABLE).buildAndRegister();

        CharcoalByproducts = GTMaterials.builder("charcoal_byproducts")
                .fluid().color(0x784421).buildAndRegister();

        Biomass = GTMaterials.builder("biomass")
                .liquid(new FluidBuilder().customStill()).color(0x00FF00).buildAndRegister();

        BioDiesel = GTMaterials.builder("bio_diesel")
                .fluid().color(0xFF8000)
                .flags(FLAMMABLE, EXPLOSIVE).buildAndRegister();

        FermentedBiomass = GTMaterials.builder("fermented_biomass")
                .liquid(new FluidBuilder().temperature(300))
                .color(0x445500)
                .buildAndRegister();

        Creosote = GTMaterials.builder("creosote")
                .liquid(new FluidBuilder().customStill().burnTime(6400)).color(0x804000)
                .flags(STICKY).buildAndRegister();

        Diesel = GTMaterials.builder("diesel")
                .liquid(new FluidBuilder().customStill()).flags(FLAMMABLE, EXPLOSIVE).buildAndRegister();

        RocketFuel = GTMaterials.builder("rocket_fuel")
                .fluid().flags(FLAMMABLE, EXPLOSIVE).color(0xBDB78C).buildAndRegister();

        Glue = GTMaterials.builder("glue")
                .liquid(new FluidBuilder().customStill()).flags(STICKY).buildAndRegister();

        Lubricant = GTMaterials.builder("lubricant")
                .liquid(new FluidBuilder().customStill()).buildAndRegister();

        McGuffium239 = GTMaterials.builder("mc_guffium_239")
                .liquid(new FluidBuilder().customStill()).buildAndRegister();

        IndiumConcentrate = GTMaterials.builder("indium_concentrate")
                .liquid()
                .color(0x0E2950).buildAndRegister();

        SeedOil = GTMaterials.builder("seed_oil")
                .liquid(new FluidBuilder().customStill())
                .color(0xFFFFFF)
                .flags(STICKY, FLAMMABLE).buildAndRegister();

        DrillingFluid = GTMaterials.builder("drilling_fluid")
                .fluid().color(0xFFFFAA).buildAndRegister();

        ConstructionFoam = GTMaterials.builder("construction_foam")
                .fluid().color(0x808080).buildAndRegister();

        SulfuricHeavyFuel = GTMaterials.builder("sulfuric_heavy_fuel")
                .liquid(new FluidBuilder().customStill()).flags(FLAMMABLE).buildAndRegister();

        HeavyFuel = GTMaterials.builder("heavy_fuel")
                .liquid(new FluidBuilder().customStill()).flags(FLAMMABLE).buildAndRegister();

        LightlyHydroCrackedHeavyFuel = GTMaterials.builder("lightly_hydro_cracked_heavy_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xFFFF00).flags(FLAMMABLE).buildAndRegister();

        SeverelyHydroCrackedHeavyFuel = GTMaterials.builder("severely_hydro_cracked_heavy_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xFFFF00).flags(FLAMMABLE).buildAndRegister();

        LightlySteamCrackedHeavyFuel = GTMaterials.builder("lightly_steam_cracked_heavy_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .flags(FLAMMABLE).buildAndRegister();

        SeverelySteamCrackedHeavyFuel = GTMaterials.builder("severely_steam_cracked_heavy_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .flags(FLAMMABLE).buildAndRegister();

        SulfuricLightFuel = GTMaterials.builder("sulfuric_light_fuel")
                .liquid(new FluidBuilder()
                        .customStill())
                .flags(FLAMMABLE).buildAndRegister();

        LightFuel = GTMaterials.builder("light_fuel")
                .liquid(new FluidBuilder().customStill()).flags(FLAMMABLE).buildAndRegister();

        LightlyHydroCrackedLightFuel = GTMaterials.builder("lightly_hydro_cracked_light_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xB7AF08).flags(FLAMMABLE).buildAndRegister();

        SeverelyHydroCrackedLightFuel = GTMaterials.builder("severely_hydro_cracked_light_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xB7AF08).flags(FLAMMABLE).buildAndRegister();

        LightlySteamCrackedLightFuel = GTMaterials.builder("lightly_steam_cracked_light_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .flags(FLAMMABLE).buildAndRegister();

        SeverelySteamCrackedLightFuel = GTMaterials.builder("severely_steam_cracked_light_fuel")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .flags(FLAMMABLE).buildAndRegister();

        SulfuricNaphtha = GTMaterials.builder("sulfuric_naphtha")
                .liquid(new FluidBuilder().customStill()).flags(FLAMMABLE).buildAndRegister();

        Naphtha = GTMaterials.builder("naphtha")
                .liquid(new FluidBuilder().customStill()).flags(FLAMMABLE).buildAndRegister();

        LightlyHydroCrackedNaphtha = GTMaterials.builder("lightly_hydro_cracked_naphtha")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xBFB608).flags(FLAMMABLE).buildAndRegister();

        SeverelyHydroCrackedNaphtha = GTMaterials.builder("severely_hydro_cracked_naphtha")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xBFB608).flags(FLAMMABLE).buildAndRegister();

        LightlySteamCrackedNaphtha = GTMaterials.builder("lightly_steam_cracked_naphtha")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xBFB608).flags(FLAMMABLE).buildAndRegister();

        SeverelySteamCrackedNaphtha = GTMaterials.builder("severely_steam_cracked_naphtha")
                .liquid(new FluidBuilder()
                        .temperature(775)
                        .customStill())
                .color(0xBFB608).flags(FLAMMABLE).buildAndRegister();

        SulfuricGas = GTMaterials.builder("sulfuric_gas")
                .gas(new FluidBuilder().customStill())
                .color(0xECDCCC).buildAndRegister();

        RefineryGas = GTMaterials.builder("refinery_gas")
                .gas(new FluidBuilder().customStill())
                .color(0xB4B4B4)
                .flags(FLAMMABLE)
                .buildAndRegister();

        LightlyHydroCrackedGas = GTMaterials.builder("lightly_hydro_cracked_gas")
                .gas(new FluidBuilder().temperature(775))
                .color(0xA0A0A0)
                .flags(FLAMMABLE)
                .buildAndRegister();

        SeverelyHydroCrackedGas = GTMaterials.builder("severely_hydro_cracked_gas")
                .gas(new FluidBuilder().temperature(775))
                .color(0xC8C8C8)
                .flags(FLAMMABLE)
                .buildAndRegister();

        LightlySteamCrackedGas = GTMaterials.builder("lightly_steam_cracked_gas")
                .gas(new FluidBuilder().temperature(775))
                .color(0xE0E0E0)
                .flags(FLAMMABLE)
                .buildAndRegister();

        SeverelySteamCrackedGas = GTMaterials.builder("severely_steam_cracked_gas")
                .gas(new FluidBuilder().temperature(775))
                .color(0xE0E0E0).flags(FLAMMABLE).buildAndRegister();

        HydroCrackedEthane = GTMaterials.builder("hydro_cracked_ethane")
                .gas(new FluidBuilder().temperature(775))
                .color(0x9696BC).flags(FLAMMABLE).buildAndRegister();

        HydroCrackedEthylene = GTMaterials.builder("hydro_cracked_ethylene")
                .gas(new FluidBuilder().temperature(775))
                .color(0xA3A3A0).flags(FLAMMABLE).buildAndRegister();

        HydroCrackedPropene = GTMaterials.builder("hydro_cracked_propene")
                .gas(new FluidBuilder().temperature(775))
                .color(0xBEA540).flags(FLAMMABLE).buildAndRegister();

        HydroCrackedPropane = GTMaterials.builder("hydro_cracked_propane")
                .gas(new FluidBuilder().temperature(775))
                .color(0xBEA540).flags(FLAMMABLE).buildAndRegister();

        HydroCrackedButane = GTMaterials.builder("hydro_cracked_butane")
                .gas(new FluidBuilder().temperature(775))
                .color(0x852C18).flags(FLAMMABLE).buildAndRegister();

        HydroCrackedButene = GTMaterials.builder("hydro_cracked_butene")
                .gas(new FluidBuilder().temperature(775))
                .color(0x993E05).flags(FLAMMABLE).buildAndRegister();

        HydroCrackedButadiene = GTMaterials.builder("hydro_cracked_butadiene")
                .gas(new FluidBuilder().temperature(775))
                .color(0xAD5203).flags(FLAMMABLE).buildAndRegister();

        SteamCrackedEthane = GTMaterials.builder("steam_cracked_ethane")
                .gas(new FluidBuilder().temperature(775))
                .color(0x9696BC).flags(FLAMMABLE).buildAndRegister();

        SteamCrackedEthylene = GTMaterials.builder("steam_cracked_ethylene")
                .gas(new FluidBuilder().temperature(775))
                .color(0xA3A3A0).flags(FLAMMABLE).buildAndRegister();

        SteamCrackedPropene = GTMaterials.builder("steam_cracked_propene")
                .gas(new FluidBuilder().temperature(775))
                .color(0xBEA540).flags(FLAMMABLE).buildAndRegister();

        SteamCrackedPropane = GTMaterials.builder("steam_cracked_propane")
                .gas(new FluidBuilder().temperature(775))
                .color(0xBEA540).flags(FLAMMABLE).buildAndRegister();

        SteamCrackedButane = GTMaterials.builder("steam_cracked_butane")
                .gas(new FluidBuilder().temperature(775))
                .color(0x852C18).flags(FLAMMABLE).buildAndRegister();

        SteamCrackedButene = GTMaterials.builder("steam_cracked_butene")
                .gas(new FluidBuilder().temperature(775))
                .color(0x993E05).flags(FLAMMABLE).buildAndRegister();

        SteamCrackedButadiene = GTMaterials.builder("steam_cracked_butadiene")
                .gas(new FluidBuilder().temperature(775))
                .color(0xAD5203).flags(FLAMMABLE).buildAndRegister();

        LPG = GTMaterials.builder("lpg")
                .liquid(new FluidBuilder().customStill())
                .color(0xFCFCAC).flags(FLAMMABLE, EXPLOSIVE).buildAndRegister();

        RawGrowthMedium = GTMaterials.builder("raw_growth_medium")
                .fluid().color(0xA47351).buildAndRegister();

        SterileGrowthMedium = GTMaterials.builder("sterilized_growth_medium")
                .fluid().color(0xAC876E).buildAndRegister();

        Oil = GTMaterials.builder("oil")
                .liquid(new FluidBuilder().block().customStill())
                .color(0x0A0A0A)
                .flags(STICKY, FLAMMABLE)
                .buildAndRegister();

        OilHeavy = GTMaterials.builder("oil_heavy")
                .liquid(new FluidBuilder().block().customStill())
                .color(0x0A0A0A)
                .flags(STICKY, FLAMMABLE)
                .buildAndRegister();

        RawOil = GTMaterials.builder("oil_medium")
                .liquid(new FluidBuilder().block().customStill())
                .color(0x0A0A0A)
                .flags(STICKY, FLAMMABLE)
                .buildAndRegister();

        OilLight = GTMaterials.builder("oil_light")
                .liquid(new FluidBuilder().block().customStill())
                .color(0x0A0A0A)
                .flags(STICKY, FLAMMABLE)
                .buildAndRegister();

        NaturalGas = GTMaterials.builder("natural_gas")
                .gas(new FluidBuilder().block().customStill())
                .flags(FLAMMABLE, EXPLOSIVE).buildAndRegister();

        Bacteria = GTMaterials.builder("bacteria")
                .fluid().color(0x808000).buildAndRegister();

        BacterialSludge = GTMaterials.builder("bacterial_sludge")
                .fluid().color(0x355E3B).buildAndRegister();

        EnrichedBacterialSludge = GTMaterials.builder("enriched_bacterial_sludge")
                .fluid().color(0x7FFF00).buildAndRegister();

        Mutagen = GTMaterials.builder("mutagen")
                .fluid().color(0x00FF7F).buildAndRegister();

        GelatinMixture = GTMaterials.builder("gelatin_mixture")
                .fluid().color(0x588BAE).buildAndRegister();

        RawGasoline = GTMaterials.builder("raw_gasoline")
                .fluid().color(0xFF6400).flags(FLAMMABLE).buildAndRegister();

        Gasoline = GTMaterials.builder("gasoline")
                .fluid().color(0xFAA500).flags(FLAMMABLE, EXPLOSIVE).buildAndRegister();

        HighOctaneGasoline = GTMaterials.builder("high_octane_gasoline")
                .fluid().color(0xFFA500).flags(FLAMMABLE, EXPLOSIVE).buildAndRegister();

        CoalGas = GTMaterials.builder("coal_gas")
                .gas().color(0x333333).buildAndRegister();

        CoalTar = GTMaterials.builder("coal_tar")
                .fluid().color(0x1A1A1A).flags(STICKY, FLAMMABLE).buildAndRegister();

        Gunpowder = GTMaterials.builder("gunpowder")
                .dust(0)
                .color(0xa4a4a4).secondaryColor(0x767676).iconSet(ROUGH)
                .flags(FLAMMABLE, EXPLOSIVE, NO_SMELTING, NO_SMASHING)
                .components(Saltpeter, 2, Sulfur, 1, Coal, 3)
                .buildAndRegister();

        Oilsands = GTMaterials.builder("oilsands")
                .dust(1).ore()
                .color(0xe3c78a).secondaryColor(0x161e22).iconSet(SAND)
                .flags(FLAMMABLE)
                .buildAndRegister();

        RareEarth = GTMaterials.builder("rare_earth")
                .dust(0)
                .color(0xffdc88).secondaryColor(0xe99673).iconSet(FINE)
                .buildAndRegister();

        Stone = GTMaterials.builder("stone")
                .dust(2)
                .color(0x8f8f8f).secondaryColor(0x898989).iconSet(ROUGH)
                .flags(MORTAR_GRINDABLE, GENERATE_GEAR, NO_SMASHING, NO_SMELTING)
                .buildAndRegister();

        Lava = GTMaterials.builder("lava")
                .fluid().color(0xFF4000).buildAndRegister();

        Netherite = GTMaterials.builder("netherite")
                .ingot().color(0x4b4042).secondaryColor(0x474447)
                .toolStats(ToolProperty.Builder.of(10.0F, 4.0F, 2032, 4)
                        .enchantability(21).build())
                .buildAndRegister();

        Glowstone = GTMaterials.builder("glowstone")
                .dust(1)
                .liquid(new FluidBuilder().temperature(500))
                .color(0xfcb34c).secondaryColor(0xce7533).iconSet(SHINY)
                .flags(NO_SMASHING, GENERATE_PLATE, EXCLUDE_PLATE_COMPRESSOR_RECIPE,
                        EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES)
                .buildAndRegister();

        NetherStar = GTMaterials.builder("nether_star")
                .gem(4)
                .color(0xfeffc6).secondaryColor(0x7fd7e2)
                .iconSet(NETHERSTAR)
                .flags(NO_SMASHING, NO_SMELTING, GENERATE_LENS)
                .buildAndRegister();

        Endstone = GTMaterials.builder("endstone")
                .dust(1)
                .color(0xf6fabd).secondaryColor(0xc5be8b).iconSet(ROUGH)
                .flags(NO_SMASHING)
                .buildAndRegister();

        Netherrack = GTMaterials.builder("netherrack")
                .dust(1)
                .color(0x7c4249).secondaryColor(0x400b0b).iconSet(ROUGH)
                .flags(NO_SMASHING, FLAMMABLE)
                .buildAndRegister();

        CetaneBoostedDiesel = GTMaterials.builder("cetane_boosted_diesel")
                .liquid(new FluidBuilder().customStill())
                .color(0xC8FF00)
                .flags(FLAMMABLE, EXPLOSIVE)
                .buildAndRegister();

        Collagen = GTMaterials.builder("collagen")
                .dust(1)
                .color(0xffadb7).secondaryColor(0x80471C).iconSet(ROUGH)
                .buildAndRegister();

        Gelatin = GTMaterials.builder("gelatin")
                .dust(1)
                .color(0xfaf7cb).secondaryColor(0x693d00).iconSet(ROUGH)
                .buildAndRegister();

        Agar = GTMaterials.builder("agar")
                .dust(1)
                .color(0xbdd168).secondaryColor(0x403218).iconSet(ROUGH)
                .buildAndRegister();

        Milk = GTMaterials.builder("milk")
                .liquid(new FluidBuilder()
                        .temperature(295)
                        .customStill())
                .color(0xfffbf0).secondaryColor(0xf6eac8).iconSet(FINE)
                .buildAndRegister();

        Cocoa = GTMaterials.builder("cocoa")
                .dust(0)
                .color(0x976746).secondaryColor(0x301a0a).iconSet(FINE)
                .buildAndRegister();

        Wheat = GTMaterials.builder("wheat")
                .dust(0)
                .color(0xdcbb65).secondaryColor(0x565138).iconSet(FINE)
                .buildAndRegister();

        Meat = GTMaterials.builder("meat")
                .dust(1)
                .color(0xe85048).secondaryColor(0x470a06).iconSet(SAND)
                .buildAndRegister();

        Wood = GTMaterials.builder("wood")
                .wood()
                .color(0xc29f6d).secondaryColor(0x643200).iconSet(WOOD)
                .fluidPipeProperties(340, 5, false)
                .toolStats(ToolProperty.Builder.of(1.0F, 1.0F, 128, 1, GTToolType.SOFT_MALLET).build())
                .flags(GENERATE_PLATE, GENERATE_ROD, GENERATE_BOLT_SCREW, GENERATE_LONG_ROD, FLAMMABLE, GENERATE_GEAR,
                        GENERATE_FRAME)
                .buildAndRegister();

        Paper = GTMaterials.builder("paper")
                .dust(0)
                .color(0xFAFAFA).secondaryColor(0x878787).iconSet(FINE)
                .flags(GENERATE_PLATE, FLAMMABLE, NO_SMELTING, NO_SMASHING,
                        MORTAR_GRINDABLE, EXCLUDE_PLATE_COMPRESSOR_RECIPE)
                .buildAndRegister();

        FishOil = GTMaterials.builder("fish_oil")
                .fluid()
                .color(0xDCC15D)
                .flags(STICKY, FLAMMABLE)
                .buildAndRegister();

        RubySlurry = GTMaterials.builder("ruby_slurry")
                .fluid().color(0xff6464).buildAndRegister();

        SapphireSlurry = GTMaterials.builder("sapphire_slurry")
                .fluid().color(0x6464c8).buildAndRegister();

        GreenSapphireSlurry = GTMaterials.builder("green_sapphire_slurry")
                .fluid().color(0x64c882).buildAndRegister();

        // These colors are much nicer looking than those in MC's EnumDyeColor
        DyeBlack = GTMaterials.builder("black_dye")
                .fluid().color(0x202020).buildAndRegister();

        DyeRed = GTMaterials.builder("red_dye")
                .fluid().color(0xFF0000).buildAndRegister();

        DyeGreen = GTMaterials.builder("green_dye")
                .fluid().color(0x00FF00).buildAndRegister();

        DyeBrown = GTMaterials.builder("brown_dye")
                .fluid().color(0x604000).buildAndRegister();

        DyeBlue = GTMaterials.builder("blue_dye")
                .fluid().color(0x0020FF).buildAndRegister();

        DyePurple = GTMaterials.builder("purple_dye")
                .fluid().color(0x800080).buildAndRegister();

        DyeCyan = GTMaterials.builder("cyan_dye")
                .fluid().color(0x00FFFF).buildAndRegister();

        DyeLightGray = GTMaterials.builder("light_gray_dye")
                .fluid().color(0xC0C0C0).buildAndRegister();

        DyeGray = GTMaterials.builder("gray_dye")
                .fluid().color(0x808080).buildAndRegister();

        DyePink = GTMaterials.builder("pink_dye")
                .fluid().color(0xFFC0C0).buildAndRegister();

        DyeLime = GTMaterials.builder("lime_dye")
                .fluid().color(0x80FF80).buildAndRegister();

        DyeYellow = GTMaterials.builder("yellow_dye")
                .fluid().color(0xFFFF00).buildAndRegister();

        DyeLightBlue = GTMaterials.builder("light_blue_dye")
                .fluid().color(0x6080FF).buildAndRegister();

        DyeMagenta = GTMaterials.builder("magenta_dye")
                .fluid().color(0xFF00FF).buildAndRegister();

        DyeOrange = GTMaterials.builder("orange_dye")
                .fluid().color(0xFF8000).buildAndRegister();

        DyeWhite = GTMaterials.builder("white_dye")
                .fluid().color(0xFFFFFF).buildAndRegister();

        ImpureEnrichedNaquadahSolution = GTMaterials.builder("impure_enriched_naquadah_solution")
                .fluid().color(0x388438).buildAndRegister();

        EnrichedNaquadahSolution = GTMaterials.builder("enriched_naquadah_solution")
                .fluid().color(0x3AAD3A).buildAndRegister();

        AcidicEnrichedNaquadahSolution = GTMaterials.builder("acidic_enriched_naquadah_solution")
                .liquid()
                .color(0x3DD63D).buildAndRegister();

        EnrichedNaquadahWaste = GTMaterials.builder("enriched_naquadah_waste")
                .fluid().color(0x355B35).buildAndRegister();

        ImpureNaquadriaSolution = GTMaterials.builder("impure_naquadria_solution")
                .fluid().color(0x518451).buildAndRegister();

        NaquadriaSolution = GTMaterials.builder("naquadria_solution")
                .fluid().color(0x61AD61).buildAndRegister();

        AcidicNaquadriaSolution = GTMaterials.builder("acidic_naquadria_solution")
                .liquid()
                .color(0x70D670).buildAndRegister();

        NaquadriaWaste = GTMaterials.builder("naquadria_waste")
                .fluid().color(0x425B42).buildAndRegister();

        Lapotron = GTMaterials.builder("lapotron")
                .gem()
                .color(0x7497ea).secondaryColor(0x1c0b39).iconSet(DIAMOND)
                .flags(NO_UNIFICATION)
                .ignoredTagPrefixes(dustTiny, dustSmall)
                .buildAndRegister();

        TreatedWood = GTMaterials.builder("treated_wood")
                .wood()
                .color(0x644218).secondaryColor(0x4e0b00).iconSet(WOOD)
                .fluidPipeProperties(340, 10, false)
                .flags(GENERATE_PLATE, FLAMMABLE, GENERATE_ROD, GENERATE_FRAME)
                .buildAndRegister();

        UUMatter = GTMaterials.builder("uu_matter")
                .liquid(new FluidBuilder()
                        .temperature(300)
                        .customStill())
                .buildAndRegister();

        PCBCoolant = GTMaterials.builder("pcb_coolant")
                .fluid().color(0xD5D69C)
                .hazard(HazardProperty.HazardTrigger.INHALATION, GTMedicalConditions.CARCINOGEN)
                .buildAndRegister();

        Sculk = GTMaterials.builder("sculk")
                .dust(1)
                .color(0x015a5c).secondaryColor(0x001616).iconSet(ROUGH)
                .buildAndRegister();

        Wax = GTMaterials.builder("wax")
                .ingot().fluid()
                .color(0xfabf29)
                .flags(NO_SMELTING)
                .buildAndRegister();

        BauxiteSlurry = GTMaterials.builder("bauxite_slurry")
                .fluid()
                .color(0x051650)
                .buildAndRegister();

        CrackedBauxiteSlurry = GTMaterials.builder("cracked_bauxite_slurry")
                .liquid(new FluidBuilder()
                        .temperature(775))
                .color(0x052C50)
                .buildAndRegister();

        BauxiteSludge = GTMaterials.builder("bauxite_sludge")
                .fluid()
                .color(0x563D2D)
                .buildAndRegister();

        DecalcifiedBauxiteSludge = GTMaterials.builder("decalcified_bauxite_sludge")
                .fluid()
                .color(0x6F2DA8)
                .buildAndRegister();

        BauxiteSlag = GTMaterials.builder("bauxite_slag")
                .dust()
                .color(0x6F2DA8).iconSet(SAND)
                .buildAndRegister();
    }
}
